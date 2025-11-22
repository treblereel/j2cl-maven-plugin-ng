package org.example.task;

import com.google.javascript.jscomp.*;
import com.google.javascript.rhino.StaticSourceFile;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.tools.ClosureCompilerWarningsGuard;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
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

    public ClosureTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.OPTIMIZED_JS;
    }

    @Override
    public void process() {
        TaskOutput selfJsOutPut = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);

        Collection<Dependency> allDependencies = getFlattenDependencies();
        TaskOutput depsUnzipped = input(allDependencies, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput depsTranspiled = input(allDependencies, OutputTypes.TRANSPILED_JS);

        List<File> extra = buildContext.getConfig().getJsZip();

        Map<String, List<String>> js = mapFromInputs(
                Stream.of(selfJsOutPut.filter(PLAIN_JS_SOURCES).files().stream(),
                                depsTranspiled.files().stream(),
                                depsUnzipped.files().stream()
                        ).flatMap(f -> f)
                        .filter(f -> PLAIN_JS_SOURCES.matches(f.getSourcePath()))
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

        List<SourceFile> externs = new ArrayList<>();
        try {
            List<SourceFile> sourceFile = fromZipInput(getExternsFromClassPath(), Charset.defaultCharset());
            externs.addAll(sourceFile);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read externs from classpath", e);
        }

        selfJsOutPut.files().forEach(f -> {
            System.out.println("Adding externs: " + f.getSourcePath());
        });

        List<SourceFile> inputs = new ArrayList<>();
        js.forEach((k, v) -> v.forEach(s -> inputs.add(SourceFile.fromFile(s))));
        extra.forEach(f -> {
            try {
                inputs.addAll(SourceFile.fromZipFile(f.toPath().toString(), Charset.defaultCharset()));
                System.out.println("Adding extra JS input from: " + f);
            } catch (IOException e) {
                throw new RuntimeException("Unable to read extra files from " + f, e);
            }
        });

        String compressedJs = runCompiler(externs, inputs, options);
        Path outPutFolder = outputPath().resolve(buildContext.getConfig().initialScriptFilename()).getParent();

        writeJSScriptToDisk(outPutFolder, compressedJs);
        copyPublicResources(selfJsOutPut, depsUnzipped, outPutFolder);
    }

    private void copyPublicResources(TaskOutput selfJsOutPut,TaskOutput depsUnzipped, Path outPutFolder) {
            List<FileEntry> resources = Stream.concat(
                    selfJsOutPut.filter(IN_META_INF_RESOURCES, IN_PUBLIC).files().stream(),
                    depsUnzipped.filter(IN_META_INF_RESOURCES, IN_PUBLIC).files().stream()
            ).toList();

            try {
                for (FileEntry resource : resources) {
                    Path outPutPath = outPutFolder.resolve(extractPublicResourcePath(resource.getSourcePath()));
                    System.out.println("Copying resource: " + resource.getAbsolutePath() + " to " + outPutPath);
                    Files.createDirectories(outPutPath.getParent());
                    Files.copy(resource.getAbsolutePath(), outPutPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                throw new RuntimeException("Unable to copy resources to output directory: " + outPutFolder, e);
            }
    }

    private void writeJSScriptToDisk(Path outPutFolder, String compressedJs) {
        Path outputJSScriptName = outputPath().resolve(buildContext.getConfig().initialScriptFilename());
        try {
            Files.createDirectories(outPutFolder);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create output directory: " + outPutFolder, e);
        }
        try {
            Files.createDirectories(outPutFolder);
            Files.writeString(outputJSScriptName, compressedJs, Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException("Unable to write output file: " + outputPath(), e);
        }
    }

    private String runCompiler(List<SourceFile> externs, List<SourceFile> inputs, CompilerOptions options) {
        Compiler compiler = new Compiler();
        Result result = compiler.compile(externs, inputs, options);

        if (!result.success) {
            for (JSError e : result.errors) {
                logger.error(e.toString());
            }
            throw new RuntimeException("Failed to compile closure");
        }

        for (JSError e : result.warnings) {
            logger.warn("Warnings: " + e.toString());
        }
        return compiler.toSource();
    }


    private InputStream getExternsFromClassPath() {
        InputStream result = getClass().getClassLoader().getResourceAsStream("externs.zip");
        if (result == null) {
            throw new RuntimeException("Failed to find externs.zip on classpath");
        }
        return result;
    }

    public List<SourceFile> fromZipInput(InputStream input, Charset inputCharset) throws IOException {
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

    private String readEntryToString(InputStream in, Charset charset) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toString(charset);
    }

    public Map<String, List<String>> mapFromInputs(Collection<FileEntry> inputs) {
        return inputs.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getParentPath().toString(),
                        Collectors.mapping(c -> c.getAbsolutePath().toString(), Collectors.toUnmodifiableList())
                ));
    }

    private Path extractPublicResourcePath(Path p) {
        int publicIndex = -1;
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals("public")) {
                publicIndex = i;
                break;
            }
        }
        return p.subpath(publicIndex + 1, p.getNameCount());
    }
}
