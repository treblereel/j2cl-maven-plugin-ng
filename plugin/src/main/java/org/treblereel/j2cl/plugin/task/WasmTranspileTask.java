package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.collect.ImmutableList;
import com.google.j2cl.common.SourceUtils;
import com.google.j2cl.transpiler.backend.Backend;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.J2CLModuleParser;
import org.treblereel.j2cl.plugin.tools.J2cl;

public class WasmTranspileTask extends TaskInput {

    private static final java.nio.file.PathMatcher JAVA_SOURCES = withSuffix(".java");
    private static final java.nio.file.PathMatcher TURBINE_OUTPUT = withSuffix("output.jar");

    public WasmTranspileTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.TRANSPILED_WASM;
    }

    @Override
    public void process() {
        TaskOutput unzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        List<Path> superSourcePaths = J2CLModuleParser.getSuperSourcePaths(unzipped.paths());

        TaskOutput stripped = input(dependency, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES);
        TaskOutput aptGenerated = input(dependency, OutputTypes.BYTECODE).filter(JAVA_SOURCES);
        TaskOutput self = new TaskOutput(Stream.concat(
                stripped.paths().stream(), aptGenerated.paths().stream()).toList());

        List<File> classpath = Stream.concat(
                        buildContext.getConfig().getExtraClasspath().stream().map(File::toString),
                        input(getFlattenDependencies(), OutputTypes.BYTECODE)
                                .filter(TURBINE_OUTPUT)
                                .files()
                                .stream()
                                .map(f -> f.getAbsolutePath().toFile().toString()))
                .distinct()
                .map(File::new)
                .toList();

        File bootstrapClasspath = buildContext.getConfig().getBootstrapClasspath();

        List<FileEntry> allJava = new ArrayList<>(self.files().stream()
                .filter(e -> JAVA_SOURCES.matches(e.getSourcePath()))
                .toList());

        if (allJava.isEmpty()) {
            allJava.addAll(resolveFromSourcesJar());
        }

        if (allJava.isEmpty()) {
            return;
        }

        List<SourceUtils.FileInfo> regularJava = allJava.stream()
                .filter(f -> superSourcePaths.stream().noneMatch(f.getSourcePath()::startsWith))
                .map(p -> SourceUtils.FileInfo.create(p.getAbsolutePath().toString(), p.getSourcePath().toString()))
                .toList();

        List<String> wasmEntryPoints = buildContext.getConfig().wasmEntryPoints();

        J2cl j2cl = new J2cl(classpath,
                bootstrapClasspath,
                outputPath().toFile(),
                logger,
                Backend.WASM,
                wasmEntryPoints);

        List<SourceUtils.FileInfo> superJava = allJava.stream()
                .filter(f -> superSourcePaths.stream().anyMatch(f.getSourcePath()::startsWith))
                .map(p -> toStrippedFileInfo(p, superSourcePaths))
                .toList();

        if (superJava.isEmpty()) {
            if (!j2cl.transpile(regularJava, ImmutableList.of())) {
                throw new RuntimeException("J2CL WASM transpilation failed for " + dependency.key());
            }
        } else {
            if (!j2cl.transpile(regularJava, ImmutableList.of())) {
                throw new RuntimeException("J2CL WASM transpilation failed for " + dependency.key());
            }

            List<File> superClasspath = new ArrayList<>(classpath);
            superClasspath.add(outputPath().toFile());

            J2cl superJ2cl = new J2cl(superClasspath,
                    bootstrapClasspath,
                    outputPath().toFile(),
                    logger,
                    Backend.WASM,
                    wasmEntryPoints);

            if (!superJ2cl.transpile(superJava, ImmutableList.of())) {
                throw new RuntimeException("J2CL WASM super-source transpilation failed for " + dependency.key());
            }
        }
    }

    private List<FileEntry> resolveFromSourcesJar() {
        try {
            String coords = dependency.groupId() + ":" + dependency.artifactId()
                    + ":jar:sources:" + dependency.version();
            File sourcesJar = buildContext.getArtifactResolver().getJarWithMavenCoords(coords);
            if (sourcesJar == null || !sourcesJar.exists()) {
                return List.of();
            }

            Path extractDir = outputPath().resolve("resolved-sources");
            Files.createDirectories(extractDir);

            try (ZipFile zf = new ZipFile(sourcesJar)) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".java")) {
                        Path target = extractDir.resolve(entry.getName());
                        Files.createDirectories(target.getParent());
                        Files.copy(zf.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            List<FileEntry> result = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(extractDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> result.add(new FileEntry(
                                extractDir.relativize(p), p, extractDir)));
            }

            if (!result.isEmpty()) {
                logger.info("Resolved " + result.size() + " sources from -sources.jar for " + dependency.key());
            }

            return result;
        } catch (Exception e) {
            logger.debug("No -sources.jar available for " + dependency.key());
            return List.of();
        }
    }

    private static SourceUtils.FileInfo toStrippedFileInfo(FileEntry entry, List<Path> superSourcePaths) {
        Path superRoot = superSourcePaths.stream()
                .filter(entry.getSourcePath()::startsWith)
                .findFirst()
                .orElseThrow();
        String targetPath = superRoot.relativize(entry.getSourcePath()).toString();
        return SourceUtils.FileInfo.create(entry.getAbsolutePath().toString(), targetPath);
    }
}
