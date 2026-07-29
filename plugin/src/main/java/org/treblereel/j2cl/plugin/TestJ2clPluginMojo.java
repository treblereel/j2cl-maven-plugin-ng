package org.treblereel.j2cl.plugin;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.XtbMessageBundle;
import com.google.javascript.rhino.StaticSourceFile;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.htmlunit.BrowserVersion;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.support.ui.FluentWait;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.log.MavenBuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.model.TestReactorDependency;
import org.treblereel.j2cl.plugin.task.OutputTypes;
import org.treblereel.j2cl.plugin.task.TaskInput;
import org.treblereel.j2cl.plugin.task.TaskInputFactory;
import org.treblereel.j2cl.plugin.tools.AptPath;
import org.treblereel.j2cl.plugin.tools.ClosureCompilerWarningsGuard;
import org.treblereel.j2cl.plugin.tools.ServiceFileReader;

@Mojo(
        name = "test",
        defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
public class TestJ2clPluginMojo extends AbstractJ2clPluginMojo {

    @Parameter(defaultValue = "SIMPLE_OPTIMIZATIONS", property = "compilationLevel")
    protected String compilationLevel;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-test", required = true)
    protected String webappDirectory;

    @Parameter(defaultValue = "${project.artifactId}/test.js", required = true)
    protected String initialScriptFilename;

    @Parameter(property = "skipTests", defaultValue = "false")
    protected boolean skipTests;

    @Parameter(property = "maven.test.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(defaultValue = "htmlunit")
    protected String webdriver;

    @Parameter(defaultValue = "true")
    protected boolean checkAssertions;

    @Parameter(defaultValue = "60", property = "j2cl.test.timeout")
    protected int testTimeout;

    @Parameter(defaultValue = "ECMASCRIPT5", property = "languageOut")
    protected String languageOut;

    private static final PathMatcher JS_SOURCES = p -> p.getFileName().toString().endsWith(".js");
    private static final PathMatcher NATIVE_JS = p -> p.getFileName().toString().endsWith(".native.js");
    private static final PathMatcher EXTERNS_JS = p -> p.getFileName().toString().endsWith(".externs.js");
    private static final Path META_INF = Paths.get("META-INF");
    private static final PathMatcher IN_META_INF = path -> path.startsWith(META_INF);

    private static final PathMatcher PLAIN_JS_SOURCES = path -> {
        if (IN_META_INF.matches(path)) return false;
        return JS_SOURCES.matches(path) && !NATIVE_JS.matches(path) && !EXTERNS_JS.matches(path);
    };

    @Override
    protected void process(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        throw new UnsupportedOperationException("TestJ2clPluginMojo uses its own execute() flow");
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Tests are skipped (maven.test.skip=true)");
            return;
        }

        TaskInput.clearCache();

        BuildLog buildLog = new MavenBuildLog(this);

        try {
            Files.createDirectories(Paths.get(webappDirectory));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create test webappDirectory " + webappDirectory, e);
        }

        Map<String, String> failedTests = new HashMap<>();

        // --- Build artifact resolver (same as parent) ---
        Map<String, org.apache.maven.artifact.Artifact> defaultDependencyReplacement = new HashMap<>();
        defaultDependencyReplacement.put("com.google.jsinterop:base", new org.apache.maven.artifact.DefaultArtifact(
                "org.kie.j2cl.tools.jsinterop", "jsinterop-base", "1.1.1",
                "compile", "jar", null, new org.apache.maven.artifact.handler.DefaultArtifactHandler()
        ));
        defaultDependencyReplacement.put("org.gwtproject:gwt-user", null);
        defaultDependencyReplacement.put("org.gwtproject:gwt-dev", null);
        defaultDependencyReplacement.put("org.gwtproject:gwt-servlet", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-user", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-dev", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-servlet", null);

        ArtifactResolver artifactResolver = new ArtifactResolver(
                project, repoSystem, remoteRepos, repoSession, session,
                projectBuilder, defaultDependencyReplacement, buildLog
        );

        // --- Assemble test classpath (includes junit-processor, junit-runtime, junit-annotations) ---
        File junitProcessorJar = getFileWithMavenCoords("org.kie.j2cl.tools:junit-processor:" + getJ2clVersion());
        File junitRuntimeJar = getFileWithMavenCoords(runtime);
        File junitAnnotationsJar = getFileWithMavenCoords(junitAnnotations);
        File junitEmulJar = getFileWithMavenCoords(junitEmul);
        File gwttestcaseEmulJar = getFileWithMavenCoords(gwttestcaseEmul);

        boolean isWasm = "WASM".equalsIgnoreCase(backend);

        List<File> extraClasspath = Arrays.asList(
                getFileWithMavenCoords(isWasm ? jreWasmJar : jreJar),
                getFileWithMavenCoords(jsinteropAnnotationsJar),
                getFileWithMavenCoords(internalAnnotationsJar),
                getFileWithMavenCoords(jsinteropBaseJar),
                getFileWithMavenCoords(jspecify),
                junitProcessorJar,
                junitRuntimeJar,
                junitAnnotationsJar,
                junitEmulJar,
                gwttestcaseEmulJar
        );

        List<Artifact> extraJsZips;
        if (isWasm) {
            extraJsZips = Arrays.asList(
                    getMavenArtifactWithCoords(bootstrapJsZip),
                    getMavenArtifactWithCoords(testJsZip),
                    getMavenArtifactWithCoords(jreWasmJsZip),
                    getMavenArtifactWithCoords(jreJsZip),
                    getMavenArtifactWithCoords(runtimeJsZip)
            );
        } else {
            extraJsZips = Arrays.asList(
                    getMavenArtifactWithCoords(testJsZip),
                    getMavenArtifactWithCoords(bootstrapJsZip),
                    getMavenArtifactWithCoords(jreJsZip),
                    getMavenArtifactWithCoords(runtimeJsZip)
            );
        }

        // --- Create APT path for junit-processor ---
        List<AptPath> testProcessors;
        try {
            testProcessors = List.of(
                    new AptPath(junitProcessorJar,
                            new ArrayList<>(ServiceFileReader.readProcessors(junitProcessorJar.toPath()))),
                    new AptPath(junitEmulJar, List.of()),
                    new AptPath(gwttestcaseEmulJar, List.of())
            );
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read junit-processor service file", e);
        }

        annotationProcessorsArgs.put("testPlatform", isWasm ? "WASM" : "CLOSURE");

        File bootstrapClasspath = getFileWithMavenCoords(isWasm ? this.bootstrapClasspathWasm : this.bootstrapClasspath);

        List<String> testWasmEntryPoints = isWasm ? List.of(".*_Adapter#.*") : wasmEntryPoints;

        File wasmJreJsZip = isWasm ? getFileWithMavenCoords(this.jreWasmJsZip) : null;

        BuildConfig buildConfig = new BuildConfig(
                extraClasspath, extraJsZips, bootstrapClasspath,
                initialScriptFilename, webappDirectory, compilationLevel,
                defines, rewritePolyfills, translationsFile, enableSourcemaps,
                languageOut, checkAssertions, env,
                annotationProcessorsArgs, testProcessors,
                backend, testWasmEntryPoints, wasmJreJsZip
        );

        BuildContext testBuildContext = new BuildContext(
                project, buildConfig, artifactResolver,
                new org.apache.maven.plugin.PluginParameterExpressionEvaluator(session, mojoExecution)
        );

        // --- Phase 1: Test Compilation and Discovery ---
        buildLog.info("Phase 1: Compiling test sources and discovering tests...");

        ReactorDependency mainDep = new ReactorDependency(project, artifactResolver);
        TestReactorDependency testDep = new TestReactorDependency(project, artifactResolver, mainDep);

        if (testDep.getSourcePaths().isEmpty()) {
            buildLog.info("No test sources found, skipping test execution");
            return;
        }

        try {
            if (isWasm) {
                TaskInputFactory.create(testDep, testBuildContext, OutputTypes.WASM_OPTIMIZED, buildLog)
                        .runTask().join();
            } else {
                TaskInputFactory.create(testDep, testBuildContext, OutputTypes.TRANSPILED_JS, buildLog)
                        .runTask().join();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to compile test sources", e);
        }

        // Read test_summary.json from bytecode output
        Path bytecodeOutput = testBuildContext.getOutputDirectory()
                .resolve(testDep.key()).resolve("bytecode");
        Path testSummaryFile = bytecodeOutput.resolve("test_summary.json");

        if (!Files.exists(testSummaryFile)) {
            buildLog.info("No test_summary.json found — no @J2clTestInput tests discovered");
            return;
        }

        final String[] generatedTests;
        try (Reader reader = new BufferedReader(new FileReader(testSummaryFile.toFile()))) {
            generatedTests = new Gson()
                    .<Map<String, String[]>>fromJson(reader, new TypeToken<Map<String, String[]>>() {}.getType())
                    .get("tests");
        } catch (IOException ex) {
            throw new MojoExecutionException("Error reading test_summary.json", ex);
        }

        if (generatedTests == null || generatedTests.length == 0) {
            buildLog.info("No tests found in test_summary.json");
            return;
        }

        buildLog.info("Discovered " + generatedTests.length + " test(s)");

        // --- Phase 2: Per-test compilation ---
        buildLog.info("Phase 2: Compiling individual tests...");

        Path outputDir = testBuildContext.getOutputDirectory();
        List<Dependency> allDeps = flattenDependencies(testDep);

        if (isWasm) {
            compileWasmTests(testDep, testBuildContext, buildLog, buildConfig,
                    generatedTests, bytecodeOutput, outputDir);
        } else {
            compileClosureTests(testDep, testBuildContext, buildLog, buildConfig,
                    generatedTests, bytecodeOutput, outputDir, allDeps);
        }

        // --- Phase 3: Test Execution ---
        if (skipTests) {
            buildLog.info("Tests compiled but execution skipped (skipTests=true)");
            return;
        }

        buildLog.info("Phase 3: Running tests...");

        HttpServer server = null;
        WebDriver driver = null;
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            Path webRoot = Paths.get(webappDirectory);
            server.createContext("/", exchange -> {
                Path file = webRoot.resolve(exchange.getRequestURI().getPath().substring(1));
                if (Files.exists(file) && !Files.isDirectory(file)) {
                    byte[] bytes = Files.readAllBytes(file);
                    String contentType = Files.probeContentType(file);
                    if (contentType == null) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".js")) contentType = "application/javascript";
                        else if (name.endsWith(".html")) contentType = "text/html";
                        else if (name.endsWith(".wasm")) contentType = "application/wasm";
                        else contentType = "application/octet-stream";
                    }
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                }
            });
            server.start();
            int port = server.getAddress().getPort();

            driver = createBrowser();

            for (String generatedTest : generatedTests) {
                String testFilePathWithoutSuffix = generatedTest.substring(0, generatedTest.length() - 3);
                String testClass = testFilePathWithoutSuffix.replace("/", ".");
                String testScriptFilename = initialScriptFilename.substring(0, initialScriptFilename.lastIndexOf(".js"))
                        + "-" + testClass + ".js";

                Path outputJsPath = Paths.get(webappDirectory).resolve(testScriptFilename);
                Path htmlPath = outputJsPath.resolveSibling(
                        outputJsPath.getFileName().toString().replace(".js", ".html"));

                if (!Files.exists(htmlPath)) {
                    buildLog.warn("HTML file not found for test: " + testClass);
                    continue;
                }

                Path relativePath = Paths.get(webappDirectory).relativize(htmlPath);
                String url = "http://localhost:" + port + "/" + relativePath.toString().replace(File.separator, "/");

                buildLog.info("Test started: " + testClass);
                buildLog.info("Fetching " + url);

                try {
                    resetBrowser(driver);
                    driver.get(url);

                    new FluentWait<>(driver)
                            .withTimeout(Duration.ofSeconds(testTimeout))
                            .withMessage("Test failed to finish before timeout: " + testClass)
                            .pollingEvery(Duration.ofMillis(100))
                            .until(TestJ2clPluginMojo::isFinished);

                    if (!isSuccess(driver)) {
                        analyzeLog(driver, buildLog);
                        failedTests.put(testClass, generatedTest);
                        buildLog.error("Test failed: " + testClass);
                    } else {
                        buildLog.info("Test passed: " + testClass);
                    }
                } catch (Exception ex) {
                    failedTests.put(testClass, generatedTest);
                    analyzeLog(driver, buildLog);
                    buildLog.error("Test failed: " + testClass);
                    buildLog.error(cleanForMavenLog(ex.getMessage()));
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run tests", e);
        } finally {
            if (server != null) {
                server.stop(0);
            }
            if (driver != null) {
                driver.quit();
            }
        }

        if (failedTests.isEmpty()) {
            buildLog.info("All tests passed successfully!");
        } else {
            failedTests.forEach((name, file) ->
                    buildLog.error(String.format("Test %s failed", name)));
            throw new MojoFailureException("At least one test failed");
        }
    }

    private List<SourceFile> collectJsSources(Path outputDir, TestReactorDependency testDep,
                                               List<Dependency> allDeps, Path testSuiteDir,
                                               BuildConfig config) throws IOException {
        List<SourceFile> inputs = new ArrayList<>();

        // Build set of dependency keys whose unzipped JS overlaps with jsZips.
        // runtimeJsZip's JS overlaps with junit-runtime's unzipped JS.
        Set<String> jsZipCoveredKeys = new HashSet<>();
        for (Dependency dep : allDeps) {
            if ("junit-runtime".equals(dep.artifactId())) {
                jsZipCoveredKeys.add(dep.key());
            }
        }

        // Test suite .js file
        collectJsFromDirectory(testSuiteDir, inputs);

        // Test project's transpiled JS and unzipped sources
        collectJsFromOutputDir(outputDir, testDep.key(), "transpiled_js", inputs);
        collectJsFromOutputDir(outputDir, testDep.key(), "unzipped", inputs);

        // All dependencies' transpiled JS and unzipped sources.
        // Skip unzipped JS for deps covered by jsZips to avoid duplicate modules.
        for (Dependency dep : allDeps) {
            collectJsFromOutputDir(outputDir, dep.key(), "transpiled_js", inputs);
            if (!jsZipCoveredKeys.contains(dep.key())) {
                collectJsFromOutputDir(outputDir, dep.key(), "unzipped", inputs);
            }
        }

        // jsZips provide pre-compiled JS (jre runtime, bootstrap, junit-runtime, closure-test)
        for (File jsZip : config.getJsZip()) {
            inputs.addAll(SourceFile.fromZipFile(jsZip.toPath().toString(), Charset.defaultCharset()));
        }

        return inputs;
    }

    private void collectJsFromOutputDir(Path outputDir, String depKey, String outputType,
                                         List<SourceFile> inputs) throws IOException {
        Path dir = outputDir.resolve(depKey).resolve(outputType);
        if (Files.exists(dir)) {
            collectJsFromDirectory(dir, inputs);
        }
    }

    private void collectJsFromDirectory(Path dir, List<SourceFile> inputs) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        Path rel = dir.relativize(p);
                        return PLAIN_JS_SOURCES.matches(rel);
                    })
                    .forEach(p -> inputs.add(SourceFile.fromFile(p.toString())));
        }
    }

    private void compileClosureTests(TestReactorDependency testDep, BuildContext testBuildContext,
                                      BuildLog buildLog, BuildConfig buildConfig,
                                      String[] generatedTests, Path bytecodeOutput,
                                      Path outputDir, List<Dependency> allDeps) throws MojoExecutionException {
        try {
            var depFutures = allDeps.stream()
                    .map(dep -> TaskInputFactory.create(dep, testBuildContext, OutputTypes.TRANSPILED_JS, buildLog).runTask())
                    .toList();
            for (var future : depFutures) {
                future.join();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to transpile test dependencies", e);
        }

        for (String generatedTest : generatedTests) {
            String testFilePathWithoutSuffix = generatedTest.substring(0, generatedTest.length() - 3);
            String testClass = testFilePathWithoutSuffix.replace("/", ".");

            buildLog.info("Compiling test: " + testClass);

            Path testSuiteFile = bytecodeOutput.resolve(testFilePathWithoutSuffix + ".testsuite");
            if (!Files.exists(testSuiteFile)) {
                buildLog.warn("Test suite file not found: " + testSuiteFile);
                continue;
            }

            try {
                Path tmpDir = Files.createTempDirectory("j2cl-test-" + testClass);
                Path testJsFile = tmpDir.resolve(testFilePathWithoutSuffix + ".js");
                Files.createDirectories(testJsFile.getParent());
                Files.copy(testSuiteFile, testJsFile);

                String testScriptFilename = initialScriptFilename.substring(0, initialScriptFilename.lastIndexOf(".js"))
                        + "-" + testClass + ".js";

                List<SourceFile> inputs = collectJsSources(outputDir, testDep, allDeps, tmpDir, buildConfig);
                List<SourceFile> externs = loadExterns();
                CompilerOptions options = createClosureOptions(testScriptFilename);

                Compiler compiler = new Compiler();
                Result result = compiler.compile(externs, inputs, options);

                if (!result.success) {
                    for (JSError e : result.errors) {
                        buildLog.error(e.toString());
                    }
                    throw new MojoExecutionException("Closure compilation failed for test: " + testClass);
                }

                Path outputJsPath = Paths.get(webappDirectory).resolve(testScriptFilename);
                Files.createDirectories(outputJsPath.getParent());
                Files.writeString(outputJsPath, compiler.toSource(), StandardCharsets.UTF_8);

                generateTestHtml(outputJsPath, testScriptFilename);

            } catch (IOException e) {
                throw new MojoExecutionException("Failed to compile test: " + testClass, e);
            }
        }
    }

    private void compileWasmTests(TestReactorDependency testDep, BuildContext testBuildContext,
                                   BuildLog buildLog, BuildConfig buildConfig,
                                   String[] generatedTests, Path bytecodeOutput,
                                   Path outputDir) throws MojoExecutionException {
        String baseName = testBuildContext.getConfig().initialScriptFilename().replace(".js", "");

        Path wasmOptimizedDir = outputDir.resolve(testDep.key()).resolve("wasm_optimized");
        Path wasmFile = wasmOptimizedDir.resolve(baseName + ".wasm");

        if (!Files.exists(wasmFile)) {
            throw new MojoExecutionException("WASM output not found: " + wasmFile);
        }

        try {
            Path wasmOutputDir = Paths.get(webappDirectory);
            Path targetWasm = wasmOutputDir.resolve(baseName + ".wasm");
            Files.createDirectories(targetWasm.getParent());
            Files.copy(wasmFile, targetWasm, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Path sourceMap = wasmOptimizedDir.resolve(baseName + ".wasm.map");
            if (Files.exists(sourceMap)) {
                Files.copy(sourceMap, wasmOutputDir.resolve(baseName + ".wasm.map"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy WASM output", e);
        }

        String wasmRelativePath = Paths.get(baseName).getFileName() + ".wasm";
        String wasmModuleName = "test.wasm.module";

        for (String generatedTest : generatedTests) {
            String testFilePathWithoutSuffix = generatedTest.substring(0, generatedTest.length() - 3);
            String testClass = testFilePathWithoutSuffix.replace("/", ".");

            buildLog.info("Compiling WASM test harness: " + testClass);

            Path testSuiteFile = bytecodeOutput.resolve(testFilePathWithoutSuffix + ".testsuite");
            if (!Files.exists(testSuiteFile)) {
                buildLog.warn("Test suite file not found: " + testSuiteFile);
                continue;
            }

            try {
                Path tmpDir = Files.createTempDirectory("j2cl-wasm-test-" + testClass);

                String testSuiteContent = Files.readString(testSuiteFile, StandardCharsets.UTF_8);
                testSuiteContent = testSuiteContent.replace("REPLACEMENT_MODULE_NAME_PLACEHOLDER", wasmModuleName);
                testSuiteContent = testSuiteContent.replace("REPLACEMENT_BUILD_PATH_PLACEHOLDER", wasmRelativePath);

                Path testJsFile = tmpDir.resolve(testFilePathWithoutSuffix + ".js");
                Files.createDirectories(testJsFile.getParent());
                Files.writeString(testJsFile, testSuiteContent, StandardCharsets.UTF_8);

                Path importsJsTxt = outputDir.resolve(testDep.key()).resolve("wasm_bundled").resolve("imports.js.txt");
                String importsContent = Files.exists(importsJsTxt)
                        ? Files.readString(importsJsTxt, StandardCharsets.UTF_8) : "";

                Path wasmLoaderFile = tmpDir.resolve("wasm_module_loader.js");
                Files.writeString(wasmLoaderFile, generateWasmModuleLoaderJs(wasmModuleName, importsContent), StandardCharsets.UTF_8);

                String testScriptFilename = initialScriptFilename.substring(0, initialScriptFilename.lastIndexOf(".js"))
                        + "-" + testClass + ".js";

                List<SourceFile> inputs = new ArrayList<>();
                collectJsFromDirectory(tmpDir, inputs);
                for (File jsZip : buildConfig.getJsZip()) {
                    inputs.addAll(SourceFile.fromZipFile(jsZip.toPath().toString(), Charset.defaultCharset()));
                }

                List<SourceFile> externs = loadExterns();
                CompilerOptions options = createWasmTestClosureOptions(testScriptFilename);

                Compiler compiler = new Compiler();
                Result result = compiler.compile(externs, inputs, options);

                if (!result.success) {
                    for (JSError e : result.errors) {
                        buildLog.error(e.toString());
                    }
                    throw new MojoExecutionException("Closure compilation failed for WASM test: " + testClass);
                }

                Path outputJsPath = Paths.get(webappDirectory).resolve(testScriptFilename);
                Files.createDirectories(outputJsPath.getParent());
                Files.writeString(outputJsPath, compiler.toSource(), StandardCharsets.UTF_8);

                generateTestHtml(outputJsPath, testScriptFilename);

            } catch (IOException e) {
                throw new MojoExecutionException("Failed to compile WASM test: " + testClass, e);
            }
        }
    }

    private String generateWasmModuleLoaderJs(String moduleName, String importsJsContent) {
        return "goog.module('" + moduleName + "');\n"
                + "\n"
                + importsJsContent + "\n"
                + "\n"
                + "const options = { 'builtins': ['js-string'], 'importedStringConstants': \"'\" };\n"
                + "\n"
                + "async function instantiateStreaming(urlOrResponse) {\n"
                + "  const response = typeof urlOrResponse == 'string' ? fetch(urlOrResponse) : urlOrResponse;\n"
                + "  const {instance} = await WebAssembly.instantiateStreaming(response, getImports(), options);\n"
                + "  return instance;\n"
                + "}\n"
                + "\n"
                + "exports = {instantiateStreaming};\n";
    }

    private List<SourceFile> loadExterns() throws MojoExecutionException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("externs.zip")) {
            if (is == null) {
                throw new MojoExecutionException("Failed to find externs.zip on classpath");
            }
            List<SourceFile> externs = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(is, Charset.defaultCharset())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.getName().endsWith(".js")) continue;
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] tmp = new byte[4096];
                    int n;
                    while ((n = zis.read(tmp)) != -1) buffer.write(tmp, 0, n);
                    externs.add(SourceFile.fromCode(entry.getName(), buffer.toString(StandardCharsets.UTF_8),
                            StaticSourceFile.SourceKind.EXTERN));
                }
            }
            return externs;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load externs", e);
        }
    }

    private CompilerOptions createClosureOptions(String scriptFilename) {
        CompilerOptions options = new CompilerOptions();
        options.setJ2clMinifierEnabled(true);
        options.setJ2clPass(CompilerOptions.J2clPassMode.AUTO);
        options.setEnvironment(CompilerOptions.Environment.valueOf(env));
        options.setClosurePass(true);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.fromString(languageOut));
        options.addWarningsGuard(new ClosureCompilerWarningsGuard());

        options.setRemoveJ2clAsserts(!checkAssertions);

        if (checkAssertions) {
            options.setDefineReplacements(new TreeMap<>(defines));
        } else {
            TreeMap<String, Object> testDefines = new TreeMap<>(defines);
            testDefines.put("jre.checks.checkLevel", "MINIMAL");
            options.setDefineReplacements(testDefines);
        }

        CompilationLevel level = CompilationLevel.fromString(compilationLevel);
        if (level != null) {
            level.setOptionsForCompilationLevel(options);
        } else {
            CompilationLevel.BUNDLE.setOptionsForCompilationLevel(options);
        }

        if (translationsFile != null) {
            File xtbFile = resolveTranslationsFile(translationsFile);
            if (xtbFile != null) {
                try (FileInputStream is = new FileInputStream(xtbFile)) {
                    InputStream source = translationsFile.isAuto()
                            ? org.treblereel.j2cl.plugin.xbt.XtbPreprocessor.preprocess(is)
                            : is;
                    options.setMessageBundle(new XtbMessageBundle(source, null));
                    if (source != is) source.close();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read translations file: " + xtbFile, e);
                }
            }
        }

        return options;
    }

    private CompilerOptions createWasmTestClosureOptions(String scriptFilename) {
        CompilerOptions options = new CompilerOptions();
        options.setClosurePass(true);
        options.setEnvironment(CompilerOptions.Environment.valueOf(env));
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
        options.addWarningsGuard(new ClosureCompilerWarningsGuard());
        options.setDependencyOptions(DependencyOptions.sortOnly());
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        return options;
    }

    private void generateTestHtml(Path outputJsPath, String testScriptFilename) throws IOException {
        try (InputStream is = TestJ2clPluginMojo.class.getResourceAsStream("/junit.html");
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            String template = CharStreams.toString(reader);
            String jsFileName = Paths.get(testScriptFilename).getFileName().toString();
            String html = template.replace("<TEST_SCRIPT>", jsFileName);

            Path htmlPath = outputJsPath.resolveSibling(
                    outputJsPath.getFileName().toString().replace(".js", ".html"));
            Files.writeString(htmlPath, html, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
            Object locale = defines.get("goog.LOCALE");
            if (locale == null) {
                getLog().warn("translationsFile auto=true but goog.LOCALE not set in defines");
                return null;
            }
            return findXtbByLocale(locale.toString());
        }
        return null;
    }

    private File findXtbByLocale(String locale) {
        String normalizedLocale = locale.replace("-", "_");
        File baseDir = project.getBasedir();

        Path[] searchRoots = {
                baseDir.toPath(),
                baseDir.toPath().resolve("src/main/java"),
                baseDir.toPath().resolve("src/main/resources")
        };

        for (Path root : searchRoots) {
            if (!Files.exists(root)) continue;
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
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
                getLog().warn("Failed to scan for XTB files in " + root + ": " + e.getMessage());
            }
        }
        getLog().warn("No XTB file found for locale '" + locale + "' in " + baseDir);
        return null;
    }

    private List<Dependency> flattenDependencies(Dependency root) {
        Set<Dependency> processed = new HashSet<>();
        List<Dependency> result = new ArrayList<>();
        Queue<Dependency> queue = new LinkedList<>(root.getDependencies());
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

    private WebDriver createBrowser() throws MojoExecutionException {
        if ("chrome".equalsIgnoreCase(webdriver)) {
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.addArguments("--headless", "--window-size=1920,1200");
            LoggingPreferences loggingPreferences = new LoggingPreferences();
            loggingPreferences.enable(LogType.BROWSER, Level.ALL);
            chromeOptions.setCapability("goog:loggingPrefs", loggingPreferences);
            return new ChromeDriver(chromeOptions);
        } else if ("htmlunit".equalsIgnoreCase(webdriver)) {
            HtmlUnitDriver driver = new HtmlUnitDriver(BrowserVersion.BEST_SUPPORTED, true);
            driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
            return driver;
        }
        throw new MojoExecutionException("Unsupported webdriver type: " + webdriver);
    }

    private void analyzeLog(WebDriver driver, BuildLog log) {
        if (driver == null) return;
        try {
            driver.manage().logs().get(LogType.BROWSER).getAll().forEach(l -> {
                if (Level.SEVERE.equals(l.getLevel())) {
                    log.error(cleanForMavenLog(l.getMessage()));
                } else {
                    log.info(cleanForMavenLog(l.getMessage()));
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void resetBrowser(WebDriver driver) {
        try {
            driver.manage().logs().get(LogType.BROWSER).getAll();
        } catch (RuntimeException ignored) {
        }
        try {
            driver.manage().deleteAllCookies();
        } catch (RuntimeException ignored) {
        }
        driver.get("about:blank");
    }

    private static boolean isSuccess(WebDriver d) {
        return (Boolean) ((JavascriptExecutor) d).executeScript("return window.G_testRunner.isSuccess()");
    }

    private static boolean isFinished(WebDriver d) {
        return (Boolean) ((JavascriptExecutor) d).executeScript(
                "return !!(window.G_testRunner && window.G_testRunner.isFinished())");
    }

    private String cleanForMavenLog(String input) {
        if (input == null) return "";
        String text = input.contains("\"") ? input.substring(input.indexOf("\"")) : input;
        text = text.replace("\"", "");
        text = text.replace("\\n", "%n");
        text = text.replace("\\u003C", "<");
        return text;
    }

    private String getJ2clVersion() {
        String coords = jreJar;
        return coords.substring(coords.lastIndexOf(':') + 1);
    }
}
