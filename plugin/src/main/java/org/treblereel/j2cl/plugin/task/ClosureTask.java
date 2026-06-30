package org.treblereel.j2cl.plugin.task;

import com.google.javascript.jscomp.*;
import com.google.javascript.jscomp.XtbMessageBundle;
import com.google.javascript.rhino.StaticSourceFile;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.tools.ClosureCompilerWarningsGuard;


import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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


    private static final List<String> GOOG_LIBRARRY = List.of("lib/base.js", "lib/goog.js", "lib/reflect.js");

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
        TaskOutput selfJsOutPutUnzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput selfJsOutPut = input(dependency, OutputTypes.TRANSPILED_JS);

        Collection<Dependency> allDependencies = getFlattenDependencies();
        TaskOutput depsUnzipped = input(allDependencies, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput depsTranspiled = input(allDependencies, OutputTypes.TRANSPILED_JS);

        List<File> extra = buildContext.getConfig().getJsZip();

        Map<String, List<String>> javascriptInputs = mapFromInputs(
                Stream.of(selfJsOutPutUnzipped.filter(PLAIN_JS_SOURCES).files().stream(),
                                selfJsOutPut.filter(PLAIN_JS_SOURCES).files().stream(),
                                depsTranspiled.files().stream(),
                                depsUnzipped.files().stream()
                        ).flatMap(f -> f)
                        .filter(f -> PLAIN_JS_SOURCES.matches(f.getSourcePath()))
                        .toList());

        List<SourceFile> externs = new ArrayList<>();
        try {
            List<SourceFile> sourceFile = fromZipInput(getExternsFromClassPath(), Charset.defaultCharset());
            externs.addAll(sourceFile);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read externs from classpath", e);
        }

        List<SourceFile> inputs = new ArrayList<>();
        javascriptInputs.forEach((k, v) -> v.forEach(s -> inputs.add(SourceFile.fromFile(s))));

        extra.forEach(f -> {
            try {
                inputs.addAll(SourceFile.fromZipFile(f.toPath().toString(), Charset.defaultCharset()));
            } catch (IOException e) {
                throw new RuntimeException("Unable to read extra files from " + f, e);
            }
        });


        CompilerOptions options = new CompilerOptions();
        options.setJ2clMinifierEnabled(true);
        options.setJ2clPass(CompilerOptions.J2clPassMode.AUTO);
        options.setEnvironment(CompilerOptions.Environment.valueOf(buildContext.getConfig().env()));
        options.setClosurePass(true);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.fromString(buildContext.getConfig().languageOut()));
        options.setRemoveJ2clAsserts(!buildContext.getConfig().checkAssertions());
        options.addWarningsGuard(new ClosureCompilerWarningsGuard());

        if (buildContext.getConfig().enableSourcemaps()) {
            options.setSourceMapOutputPath("sources/" + Paths.get(buildContext.getConfig().initialScriptFilename()).getFileName() + ".map");
            options.setSourceMapIncludeSourcesContent(false);
            options.setSourceMapDetailLevel(SourceMap.DetailLevel.ALL);
            options.setSourceMapFormat(SourceMap.Format.V3);
            options.setApplyInputSourceMaps(true);

            List<SourceMap.LocationMapping> mappings = new ArrayList<>();
            Set<String> fixedPath = new HashSet<>();
            for (FileEntry file : selfJsOutPut.files()) {
                fixedPath.add(file.getParentPath().toString());
            }
            for (FileEntry file : depsTranspiled.files()) {
                fixedPath.add(file.getParentPath().toString());
            }
            for (FileEntry file : depsUnzipped.files()) {
                fixedPath.add(file.getParentPath().toString());
            }
            for (File f : extra) {
                String zipPrefix = f.toPath().toString() + "!/";
                String commonDir = null;
                for (SourceFile sf : inputs) {
                    if (!sf.getName().startsWith(zipPrefix)) continue;
                    String internal = sf.getName().substring(zipPrefix.length());
                    String dir = internal.contains("/") ? internal.substring(0, internal.lastIndexOf('/')) : "";
                    if (commonDir == null) {
                        commonDir = dir;
                    } else {
                        while (!commonDir.isEmpty() && !dir.startsWith(commonDir)) {
                            int lastSlash = commonDir.lastIndexOf('/');
                            commonDir = lastSlash >= 0 ? commonDir.substring(0, lastSlash) : "";
                        }
                    }
                }
                if (commonDir != null && !commonDir.isEmpty()) {
                    fixedPath.add(zipPrefix + commonDir);
                }
            }
            fixedPath.forEach(p -> mappings.add(new SourceMap.PrefixLocationMapping(p, ".")));
            options.setSourceMapLocationMappings(mappings);
        }

        if (buildContext.getConfig().checkAssertions()) {
            options.setDefineReplacements(buildContext.getConfig().defines());
        } else {
            Map<String, Object> defs = new TreeMap<>(buildContext.getConfig().defines());
            defs.put("jre.checks.checkLevel", "MINIMAL");
            options.setDefineReplacements(defs);
        }
        setCompilationLevel(options);
        setTranslationsFile(options);

        // Closure Library files (base.js, long.js, reflect.js) are provided via the bootstrap jsZip

        Compiler compiler = runCompiler(externs, inputs, options);
        Path outPutFolder = outputPath().resolve(buildContext.getConfig().initialScriptFilename()).getParent();

        writeJSScriptToDisk(outPutFolder, compiler.toSource());

        if (buildContext.getConfig().enableSourcemaps()) {
            writeSourceMapToDisk(outPutFolder, compiler.getSourceMap());
        }
        copyPublicResources(selfJsOutPutUnzipped, depsUnzipped, outPutFolder);
    }

    private void copyPublicResources(TaskOutput selfJsOutPut, TaskOutput depsUnzipped, Path outPutFolder) {
        List<FileEntry> resources = Stream.concat(
                selfJsOutPut.filter(IN_META_INF_RESOURCES, IN_PUBLIC).files().stream(),
                depsUnzipped.filter(IN_META_INF_RESOURCES, IN_PUBLIC).files().stream()
        ).toList();

        try {
            for (FileEntry resource : resources) {
                Path outPutPath = outPutFolder.resolve(extractPublicResourcePath(resource.getSourcePath()));
                logger.info("Copying resource: " + resource.getAbsolutePath() + " to " + outPutPath);
                Files.createDirectories(outPutPath.getParent());
                Files.copy(resource.getAbsolutePath(), outPutPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
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

    private void writeSourceMapToDisk(Path outPutFolder, SourceMap sourceMap) {
        String scriptName = buildContext.getConfig().initialScriptFilename();
        Path outputJSScriptName = outputPath().resolve(scriptName);
        String scriptBaseName = Paths.get(scriptName).getFileName().toString();
        String sourceMapFileName = scriptBaseName + ".map";
        Path outputSourceMapName = outputJSScriptName.getParent().resolve("sources").resolve(sourceMapFileName);
        try {
            Files.createDirectories(outputSourceMapName.getParent());
            try (Writer out = Files.newBufferedWriter(
                    outputSourceMapName,
                    StandardCharsets.UTF_8
            )) {
                sourceMap.appendTo(out, scriptBaseName);
                String mapLine = "\n//# sourceMappingURL=sources/" + sourceMapFileName + "\n";

                Files.writeString(
                        outputJSScriptName,
                        mapLine,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND
                );            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to write output file: " + outputPath(), e);
        }
    }

    private Compiler runCompiler(List<SourceFile> externs, List<SourceFile> inputs, CompilerOptions options) {
        Objects.requireNonNull(options);
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
        return compiler;
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
                if (!entryName.endsWith(".js")) {
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

    private void setCompilationLevel(CompilerOptions options) {
        String levelStr = buildContext.getConfig().compilationLevel();
        CompilationLevel level = CompilationLevel.fromString(levelStr);
        if (level == null) {
            throw new IllegalArgumentException("Invalid compilationLevel: " + levelStr
                    + ". Valid values: ADVANCED_OPTIMIZATIONS, SIMPLE_OPTIMIZATIONS, WHITESPACE_ONLY, BUNDLE");
        }
        level.setOptionsForCompilationLevel(options);
        logger.info("Using compilation level: " + level);
    }

    private void setTranslationsFile(CompilerOptions options) {
        org.treblereel.j2cl.plugin.xbt.TranslationsFileConfig tf = buildContext.getConfig().translationsFile();
        if (tf == null) return;

        File xtbFile = resolveTranslationsFile(tf);
        if (xtbFile == null) return;

        logger.info("Using translations file: " + xtbFile.getAbsolutePath());
        try (FileInputStream is = new FileInputStream(xtbFile)) {
            InputStream source = tf.isAuto()
                    ? org.treblereel.j2cl.plugin.xbt.XtbPreprocessor.preprocess(is)
                    : is;
            options.setMessageBundle(new XtbMessageBundle(source, null));
            if (source != is) source.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read translations file: " + xtbFile, e);
        }
    }

    private File resolveTranslationsFile(org.treblereel.j2cl.plugin.xbt.TranslationsFileConfig tf) {
        if (tf.getFile() != null && !tf.getFile().isEmpty()) {
            File f = new File(tf.getFile());
            if (!f.exists()) {
                throw new RuntimeException("Translations file not found: " + f.getAbsolutePath());
            }
            return f;
        }
        if (tf.isAuto()) {
            Object locale = buildContext.getConfig().defines().get("goog.LOCALE");
            if (locale == null) {
                logger.warn("translationsFile auto=true but goog.LOCALE not set in defines");
                return null;
            }
            return findXtbByLocale(locale.toString());
        }
        return null;
    }

    private File findXtbByLocale(String locale) {
        String normalizedLocale = locale.replace("-", "_");
        File baseDir = buildContext.getProjectBaseDir();

        Path[] searchRoots = {
                baseDir.toPath(),
                baseDir.toPath().resolve("src/main/java"),
                baseDir.toPath().resolve("src/main/resources")
        };

        for (Path root : searchRoots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                Optional<Path> match = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".xtb"))
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.contains(locale) || name.contains(normalizedLocale);
                        })
                        .findFirst();
                if (match.isPresent()) {
                    return match.get().toFile();
                }
            } catch (IOException e) {
                logger.warn("Failed to scan for XTB files in " + root + ": " + e.getMessage());
            }
        }
        logger.warn("No XTB file found for locale '" + locale + "' in " + baseDir);
        return null;
    }
}
