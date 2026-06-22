package org.example.task;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.ClosureBundler;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.transpile.BaseTranspiler;
import com.google.javascript.jscomp.transpile.Transpiler;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ClosureBundleTask extends TaskInput {

    public static final String BUNDLE_JS_EXTENSION = ".bundle.js";

    private static final Path META_INF = java.nio.file.Paths.get("META-INF");
    private static final Path PUBLIC = java.nio.file.Paths.get("public");
    private static final PathMatcher JS_SOURCES = withSuffix(".js");
    private static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");
    private static final PathMatcher EXTERNS_SOURCES = withSuffix(".externs.js");
    private static final PathMatcher IN_META_INF = path -> path.startsWith(META_INF);
    private static final PathMatcher IN_PUBLIC = path -> StreamSupport.stream(path.spliterator(), false).anyMatch(PUBLIC::equals);

    static final PathMatcher PLAIN_JS_SOURCES = new PathMatcher() {
        @Override
        public boolean matches(Path path) {
            if (IN_META_INF.matches(path)) return false;
            if (IN_PUBLIC.matches(path)) return false;
            return JS_SOURCES.matches(path) && !NATIVE_JS_SOURCES.matches(path) && !EXTERNS_SOURCES.matches(path);
        }

        @Override
        public String toString() {
            return "Only non-native JS sources";
        }
    };

    public ClosureBundleTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.BUNDLED_JS;
    }

    @Override
    public void process() {
        TaskOutput transpiled = input(dependency, OutputTypes.TRANSPILED_JS);
        TaskOutput unzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);

        List<FileEntry> jsFiles = Stream.concat(
                        transpiled.files().stream(),
                        unzipped.files().stream()
                )
                .filter(f -> PLAIN_JS_SOURCES.matches(f.getSourcePath()))
                .toList();

        if (jsFiles.isEmpty()) {
            try {
                Files.createFile(outputPath().resolve(safeFileName() + BUNDLE_JS_EXTENSION));
            } catch (IOException e) {
                throw new RuntimeException("Failed to create empty bundle for " + dependency.key(), e);
            }
            return;
        }

        Path sourcesDir = outputPath().resolve("sources");
        try {
            Files.createDirectories(sourcesDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create sources dir", e);
        }

        for (FileEntry fe : jsFiles) {
            try {
                Path target = sourcesDir.resolve(fe.getSourcePath());
                Files.createDirectories(target.getParent());
                Files.copy(fe.getAbsolutePath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy source file " + fe.getSourcePath(), e);
            }
        }

        List<DependencyInfoAndSource> dependencyInfos = new ArrayList<>();
        Compiler jsCompiler = new Compiler(System.err);

        for (FileEntry file : jsFiles) {
            CompilerInput input = new CompilerInput(SourceFile.builder()
                    .withPath(sourcesDir.resolve(file.getSourcePath()))
                    .withOriginalPath(file.getSourcePath().toString())
                    .build());
            input.setCompiler(jsCompiler);
            dependencyInfos.add(new DependencyInfoAndSource(input, input::getCode));
        }

        try {
            SortedDependencies<DependencyInfoAndSource> sorter = new SortedDependencies<>(dependencyInfos);

            Set<String> sourceRoots = new HashSet<>();
            for (FileEntry fe : jsFiles) {
                sourceRoots.add(fe.getParentPath().toString());
            }

            ClosureBundler bundler = new ClosureBundler(Transpiler.NULL, new BaseTranspiler(
                    new BaseTranspiler.CompilerSupplier(
                            CompilerOptions.LanguageMode.ECMASCRIPT_NEXT.toFeatureSet().without(FeatureSet.Feature.MODULES),
                            ModuleLoader.ResolutionMode.BROWSER,
                            ImmutableList.copyOf(sourceRoots),
                            ImmutableMap.of()
                    ),
                    ""
            )).useEval(true);

            Path outputFile = outputPath().resolve(safeFileName() + BUNDLE_JS_EXTENSION);
            try (BufferedWriter bundleOut = new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(outputFile), StandardCharsets.UTF_8))) {
                for (DependencyInfoAndSource info : sorter.getSortedList()) {
                    String code = info.getSource();
                    String name = info.getName();

                    if (Compiler.isFillFileName(name) && code.isEmpty()) {
                        continue;
                    }

                    bundleOut.append("//").append(name).append("\n");
                    bundler.withPath(name).withSourceUrl("sources/" + name).appendTo(bundleOut, info, code);
                    bundleOut.append("\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to bundle dependency " + dependency.key(), e);
        }
    }

    private String safeFileName() {
        return dependency.key().replaceAll("[^\\-_a-zA-Z0-9.]", "-");
    }

    public interface SourceSupplier {
        String get() throws IOException;
    }

    public static class DependencyInfoAndSource implements DependencyInfo {
        private final DependencyInfo delegate;
        private final SourceSupplier sourceSupplier;

        public DependencyInfoAndSource(DependencyInfo delegate, SourceSupplier sourceSupplier) {
            this.delegate = delegate;
            this.sourceSupplier = sourceSupplier;
        }

        public String getSource() throws IOException {
            return sourceSupplier.get();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getPathRelativeToClosureBase() {
            return delegate.getPathRelativeToClosureBase();
        }

        @Override
        public ImmutableList<String> getProvides() {
            return delegate.getProvides();
        }

        @Override
        public ImmutableList<Require> getRequires() {
            return delegate.getRequires();
        }

        @Override
        public ImmutableList<String> getRequiredSymbols() {
            return delegate.getRequiredSymbols();
        }

        @Override
        public ImmutableList<String> getTypeRequires() {
            return delegate.getTypeRequires();
        }

        @Override
        public ImmutableMap<String, String> getLoadFlags() {
            return delegate.getLoadFlags();
        }

        @Override
        public boolean isEs6Module() {
            return delegate.isEs6Module();
        }

        @Override
        public boolean isGoogModule() {
            return delegate.isGoogModule();
        }

        @Override
        public boolean getHasExternsAnnotation() {
            return delegate.getHasExternsAnnotation();
        }

        @Override
        public boolean getHasNoCompileAnnotation() {
            return delegate.getHasNoCompileAnnotation();
        }
    }
}
