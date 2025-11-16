package org.example.task;

import com.google.javascript.jscomp.*;
import com.google.javascript.rhino.StaticSourceFile;
import org.example.context.BuildContext;
import org.example.model.Dependency;
import org.example.tools.Closure;
import org.example.tools.ClosureCompilerWarningsGuard;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ClosureTask extends TaskInput {

    private static final Path META_INF = Paths.get("META-INF");
    /**
     * servlet 3 and webjars convention
     */
    private static final Path META_INF_RESOURCES = META_INF.resolve("resources");
    /**
     * optional directory to offer externs within a jar
     */
    private static final Path META_INF_EXTERNS = META_INF.resolve("externs");

    private static final Path PUBLIC = Paths.get("public");

    private static final PathMatcher JS_SOURCES = withSuffix(".js");

    private static final PathMatcher XTB = withSuffix(".xtb");
    private static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");
    private static final PathMatcher EXTERNS_SOURCES = withSuffix(".externs.js");

    private static final PathMatcher IN_META_INF = path -> path.startsWith(META_INF);
    private static final PathMatcher IN_META_INF_EXTERNS = path -> path.startsWith(META_INF_EXTERNS);
    private static final PathMatcher IN_META_INF_RESOURCES = path -> path.startsWith(META_INF_RESOURCES);

    private static final PathMatcher IN_PUBLIC = path -> StreamSupport.stream(path.spliterator(), false).anyMatch(PUBLIC::equals);

    /**
     * JS files that closure should use as type information
     */
    private static final PathMatcher EXTERNS = new PathMatcher() {
        @Override
        public boolean matches(Path path) {
            return IN_META_INF_EXTERNS.matches(path) || EXTERNS_SOURCES.matches(path);
        }

        @Override
        public String toString() {
            return "externs to pass to closure";
        }
    };

    /**
     * JS files that closure should accept as input to bundle/compile.
     */
    private static final PathMatcher PLAIN_JS_SOURCES = new PathMatcher() {
        @Override
        public boolean matches(Path path) {
            if (IN_META_INF.matches(path) && !IN_META_INF_EXTERNS.matches(path)) {
                return false;
            }
            if (IN_PUBLIC.matches(path)) {
                return false;
            }
            return JS_SOURCES.matches(path) && !NATIVE_JS_SOURCES.matches(path) && !EXTERNS.matches(path);
        }

        @Override
        public String toString() {
            return "Only non-native JS sources";
        }
    };

    public ClosureTask(Dependency dep, BuildContext buildContext) {
        super(dep, buildContext);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.OPTIMIZED_JS;
    }

    @Override
    public void process() {
        Collection<Dependency> allDependencies = getAllDependencies();

        TaskOutput depsUnzipped = input(allDependencies, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput depsTranspiled = input(allDependencies, OutputTypes.TRANSPILED_JS);

        var extra = buildContext.getConfig().getJsZip();

        Map<String, List<String>> js = Closure.mapFromInputs(
                Stream.concat(
                                depsTranspiled.files().stream(),
                                depsUnzipped.files().stream()
                        ).filter(f -> PLAIN_JS_SOURCES.matches(f.getSourcePath()))
                        .toList());

        CompilerOptions options = new CompilerOptions();
        options.setJ2clMinifierEnabled(true);
        options.setJ2clPass(CompilerOptions.J2clPassMode.AUTO);
        options.setEnvironment(CompilerOptions.Environment.BROWSER);
        CompilationLevel.ADVANCED_OPTIMIZATIONS
                .setOptionsForCompilationLevel(options);
        options.setClosurePass(true);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.addWarningsGuard(new ClosureCompilerWarningsGuard());

        SourceFile.fromCode("externs.zip", "", StaticSourceFile.SourceKind.EXTERN);

        List<SourceFile> externs = new ArrayList<>();
        try {
            List<SourceFile> sourceFile = fromZipInput("externs.zip", getExternsFromClassPath(), Charset.defaultCharset());
            externs.addAll(sourceFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        List<SourceFile> inputs = new ArrayList<>();

        js.forEach((k, v) -> {
            v.forEach(s -> {
                inputs.add(SourceFile.fromFile(s));
            });
        });

        extra.forEach(f -> {
            try {
                inputs.addAll(SourceFile.fromZipFile(f.toPath().toString(), Charset.defaultCharset()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Compiler compiler = new Compiler();
        Result result = compiler.compile(externs, inputs, options);

        if (!result.success) {
            for (JSError e : result.errors) {
                System.out.println("ERROR: " + e.toString());
            }

            throw new RuntimeException("Failed to compile closure");
        }

        for (JSError e : result.warnings) {
            System.out.println("Warnings: " + e.toString());
        }

        String compressedJs = compiler.toSource();
        System.out.println("Compressed JS: \n\n" + compressedJs);

/*        Closure closureCompiler = new Closure((BuildLog) buildContext.getConfig());
        boolean success = closureCompiler.compile(
                CompilationLevel.ADVANCED_OPTIMIZATIONS,
                DependencyOptions.DependencyMode.PRUNE,
                CompilerOptions.LanguageMode.ECMASCRIPT_NEXT,
                js,
                outputPath().toFile(),
                List.of(),
                Map.of(),
                List.of(),
                Optional.empty(),
                true,
                true,
                true,
                false,
                "BROWSER",
                "test.js",
                extra
        );

        if (!success) {
            throw new RuntimeException("Closure compilation failed");
        }*/
    }


    private Collection<Dependency> getAllDependencies() {
        Set<Dependency> processed = new HashSet<>();
        Set<Dependency> result = new HashSet<>();
        Queue<Dependency> queue = new LinkedList<>();
        queue.add(dependency);
        while (!queue.isEmpty()) {
            Dependency current = queue.poll();
            if (!processed.contains(current)) {
                result.add(current);
                processed.add(current);
                queue.addAll(current.getDependencies());
            }
        }
        return result;
    }

    private void extracted(File zip) {
        try (ZipFile zipFile = new ZipFile(zip)) {
            zipFile.stream().forEach(entry -> {
                try {
                    File outFile = outputPath().resolve(entry.getName()).toFile();
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        outFile.getParentFile().mkdirs();
                        try (InputStream is = zipFile.getInputStream(entry);
                             OutputStream os = new FileOutputStream(outFile)) {
                            is.transferTo(os);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to unzip file " + zip + " at dependency " + dependency.key(), e);
        }
    }

    private InputStream getExternsFromClassPath() {
        InputStream result = getClass().getClassLoader().getResourceAsStream("externs.zip");
        if (result == null) {
            throw new RuntimeException("Failed to find externs.zip on classpath");
        }
        return result;
    }

    public static List<SourceFile> fromZipInput(
            String zipName, InputStream input, Charset inputCharset) throws IOException {
        List<SourceFile> sourceFiles = new ArrayList<>();

        try (ZipInputStream in = new ZipInputStream(input, inputCharset)) {
            ZipEntry zipEntry;
            while ((zipEntry = in.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                if (!entryName.endsWith(".js")) { // Only accept js files
                    continue;
                }
                String code = readEntryToString(in, inputCharset);
                SourceFile sourceFile = SourceFile.fromCode(entryName, code, StaticSourceFile.SourceKind.EXTERN);
                sourceFiles.add(sourceFile);
            }
        }
        return sourceFiles;
    }

    private static String readEntryToString(InputStream in, Charset charset) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toString(charset);
    }
}
