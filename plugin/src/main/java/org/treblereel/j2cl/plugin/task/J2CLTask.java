package org.treblereel.j2cl.plugin.task;

import com.google.j2cl.common.SourceUtils;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.J2CLModuleParser;
import org.treblereel.j2cl.plugin.tools.J2cl;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class J2CLTask extends TaskInput {

    public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
    public static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");
    public static final PathMatcher TURBINE_OUTPUT = withSuffix("output.jar");

    public J2CLTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.TRANSPILED_JS;
    }

    @Override
    public void process() {
        TaskOutput unzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        List<Path> superSourcePaths = J2CLModuleParser.getSuperSourcePaths(unzipped.paths());

        TaskOutput self = input(dependency, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES);

        Stream<FileEntry> selfNative = Stream.concat(
                unzipped.filter(NATIVE_JS_SOURCES).files().stream(),
                input(dependency, OutputTypes.BYTECODE).filter(NATIVE_JS_SOURCES).files().stream()
        );

        List<File> moduleDependencies = Stream.concat(
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

        List<FileEntry> allJava = self.files().stream()
                .filter(e -> JAVA_SOURCES.matches(e.getSourcePath()))
                .toList();

        List<SourceUtils.FileInfo> regularJava = allJava.stream()
                .filter(f -> superSourcePaths.stream().noneMatch(f.getSourcePath()::startsWith))
                .map(p -> SourceUtils.FileInfo.create(p.getAbsolutePath().toString(), p.getSourcePath().toString()))
                .toList();

        List<FileEntry> allNative = selfNative.toList();

        List<SourceUtils.FileInfo> regularNative = allNative.stream()
                .filter(f -> superSourcePaths.stream().noneMatch(f.getSourcePath()::startsWith))
                .map(p -> SourceUtils.FileInfo.create(p.getAbsolutePath().toString(), p.getSourcePath().toString()))
                .toList();

        J2cl j2cl = new J2cl(moduleDependencies,
                bootstrapClasspath,
                outputPath().toFile(),
                logger);

        if (!j2cl.transpile(regularJava, regularNative)) {
            throw new RuntimeException("J2CL transpilation failed");
        }

        List<SourceUtils.FileInfo> superJava = allJava.stream()
                .filter(f -> superSourcePaths.stream().anyMatch(f.getSourcePath()::startsWith))
                .map(p -> toStrippedFileInfo(p, superSourcePaths))
                .toList();

        if (!superJava.isEmpty()) {
            List<SourceUtils.FileInfo> superNative = allNative.stream()
                    .filter(f -> superSourcePaths.stream().anyMatch(f.getSourcePath()::startsWith))
                    .map(p -> toStrippedFileInfo(p, superSourcePaths))
                    .toList();

            List<File> superClasspath = new ArrayList<>(moduleDependencies);
            superClasspath.add(outputPath().toFile());

            J2cl superJ2cl = new J2cl(superClasspath,
                    bootstrapClasspath,
                    outputPath().toFile(),
                    logger);

            if (!superJ2cl.transpile(superJava, superNative)) {
                throw new RuntimeException("J2CL super-source transpilation failed");
            }
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
