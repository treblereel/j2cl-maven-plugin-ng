package org.treblereel.j2cl.plugin.tools;

import com.google.common.collect.ImmutableList;
import com.google.j2cl.common.OutputUtils;
import com.google.j2cl.common.Problems;
import com.google.j2cl.common.SourceUtils;
import com.google.j2cl.transpiler.J2clTranspiler;
import com.google.j2cl.transpiler.J2clTranspilerOptions;
import com.google.j2cl.transpiler.ast.TypeDescriptors;
import com.google.j2cl.transpiler.backend.Backend;
import com.google.j2cl.transpiler.frontend.Frontend;
import org.treblereel.j2cl.plugin.log.BuildLog;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class J2cl {

    private final J2clTranspilerOptions.Builder optionsBuilder;
    private final File jsOutDir;
    private final BuildLog log;
    private final List<String> patchModuleJavacOptions;

    private static final Map<String, String> NON_BASE_MODULE_PREFIXES = Map.of(
            "java/sql/", "java.sql",
            "javax/sql/", "java.sql",
            "java/util/logging/", "java.logging",
            "javax/annotation/processing/", "java.compiler",
            "javax/lang/model/", "java.compiler",
            "javax/tools/", "java.compiler"
    );

    public J2cl(List<File> strippedClasspath, @Nonnull File bootstrap, File jsOutDir, BuildLog log) {
        this.jsOutDir = jsOutDir;
        this.log = log;
        Path bootstrapPath = resolveFile(bootstrap);
        this.patchModuleJavacOptions = buildPatchModuleOptions(bootstrapPath);
        optionsBuilder = J2clTranspilerOptions.builder()
                .setFrontend(Frontend.JAVAC)
                .setBackend(Backend.CLOSURE)
                .setClasspaths(Stream.concat(Stream.of(bootstrap), strippedClasspath.stream())
                        .map(J2cl::resolveFile)
                        .toList()
                )
                .setNullMarkedSupported(true)
                .setEmitReadableLibraryInfo(false)
                .setEmitReadableSourceMap(true)
                .setGenerateKytheIndexingMetadata(false)
                .setForbiddenAnnotations(ImmutableList.of());
    }

    private static Path resolveFile(File file) {
        if (Files.exists(file.toPath().resolve("output.jar"))) {
            return file.toPath().resolve("output.jar");
        }
        return file.toPath();
    }

    public boolean transpile(List<SourceUtils.FileInfo> sourcesToCompile, List<SourceUtils.FileInfo> nativeSources) {
        List<String> javacOptions = augmentWithModuleSources(sourcesToCompile);
        Problems problems = new Problems();
        try (OutputUtils.Output output = OutputUtils.initOutputForBazel(jsOutDir.toPath(), problems)) {
            J2clTranspilerOptions options = optionsBuilder
                    .setOutput(output)
                    .setSources(sourcesToCompile)
                    .setNativeSources(nativeSources)
                    .setKotlincOptions(ImmutableList.of())
                    .setWasmEntryPointStrings(ImmutableList.of())
                    .setObjCNamePrefix("J2kt")
                    .setJavacOptions(javacOptions)
                    .setEnableKlibs(false)
                    .setFriendKlibs(List.of())
                    .setDependencyKlibs(List.of())
                    .setAnnotationProcessorPath(List.of(jsOutDir.toPath()))
                    .setAnnotationProcessors(List.of())
                    .build(problems);

            log.debug(options.toString());

            runTranspiler(options, problems);
        } catch (Problems.Exit e) {
            // Program aborted due to errors recorded in problems, will be logged below
        } finally {
            TypeDescriptors.reset();
        }

        if (problems.hasErrors() || problems.hasWarnings()) {
            problems.getWarnings().forEach(log::warn);
            problems.getErrors().forEach(log::error);
        } else {
            problems.getInfoMessages().forEach(log::info);
        }
        return !problems.hasErrors();
    }

    private void runTranspiler(J2clTranspilerOptions options, Problems problems) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                J2clTranspiler.transpile(options, problems);
            } catch (Problems.Exit e) {
                // handled by caller
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                TypeDescriptors.reset();
            }
        }, "j2cl-transpiler");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running J2CL", e);
        }
        Throwable thrown = failure.get();
        if (thrown instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        if (thrown != null) {
            throw new IllegalStateException("Error while running J2CL", thrown);
        }
    }

    private static List<String> buildPatchModuleOptions(Path bootstrapJarPath) {
        try {
            Path patchDir = Files.createTempDirectory("j2cl-patch-modules");
            Map<String, Path> moduleDirs = new HashMap<>();

            try (JarFile jar = new JarFile(bootstrapJarPath.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || name.equals("module-info.class")) continue;

                    String module = getModuleForEntry(name);
                    if (module == null) continue;

                    Path moduleDir = moduleDirs.computeIfAbsent(module,
                            m -> patchDir.resolve(m.replace('.', '_')));
                    Path target = moduleDir.resolve(name);
                    Files.createDirectories(target.getParent());
                    try (var is = jar.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            List<String> options = new ArrayList<>();
            for (var moduleEntry : moduleDirs.entrySet()) {
                options.add("--patch-module");
                options.add(moduleEntry.getKey() + "=" + moduleEntry.getValue());
                if (!"java.base".equals(moduleEntry.getKey())) {
                    options.add("--add-reads");
                    options.add(moduleEntry.getKey() + "=ALL-UNNAMED");
                }
            }
            return options;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare patch-module options from " + bootstrapJarPath, e);
        }
    }

    private List<String> augmentWithModuleSources(List<SourceUtils.FileInfo> sources) {
        Map<String, Set<String>> additionalRoots = new LinkedHashMap<>();
        for (var source : sources) {
            String targetPath = source.targetPath();
            String module = getModuleForEntry(targetPath);
            if (module == null) continue;

            Path srcPath = Path.of(source.sourcePath());
            Path targetAsPath = Path.of(targetPath);
            Path sourceRoot = srcPath;
            for (int i = 0; i < targetAsPath.getNameCount(); i++) {
                sourceRoot = sourceRoot.getParent();
            }
            additionalRoots.computeIfAbsent(module, k -> new LinkedHashSet<>())
                    .add(sourceRoot.toString());
        }

        if (additionalRoots.isEmpty()) {
            return patchModuleJavacOptions;
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < patchModuleJavacOptions.size(); i++) {
            String opt = patchModuleJavacOptions.get(i);
            result.add(opt);
            if ("--patch-module".equals(opt) && i + 1 < patchModuleJavacOptions.size()) {
                String value = patchModuleJavacOptions.get(++i);
                int eq = value.indexOf('=');
                String module = value.substring(0, eq);
                Set<String> extra = additionalRoots.remove(module);
                if (extra != null) {
                    value = value + File.pathSeparator + String.join(File.pathSeparator, extra);
                }
                result.add(value);
            }
        }
        for (var entry : additionalRoots.entrySet()) {
            result.add("--patch-module");
            result.add(entry.getKey() + "=" + String.join(File.pathSeparator, entry.getValue()));
            if (!"java.base".equals(entry.getKey())) {
                result.add("--add-reads");
                result.add(entry.getKey() + "=ALL-UNNAMED");
            }
        }
        return result;
    }

    private static String getModuleForEntry(String name) {
        for (var entry : NON_BASE_MODULE_PREFIXES.entrySet()) {
            if (name.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (name.startsWith("java/")) {
            return "java.base";
        }
        return null;
    }
}
