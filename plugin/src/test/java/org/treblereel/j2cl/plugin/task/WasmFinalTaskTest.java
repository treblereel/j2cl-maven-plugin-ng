package org.treblereel.j2cl.plugin.task;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmFinalTaskTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {""})
    void publishesExecutableImportsAndLoaderWithSeparateCompileOptions(String locale) throws Exception {
        Path bundled = Files.createDirectories(directory.resolve("bundled"));
        Files.writeString(bundled.resolve("imports.js.txt"),
                "const api = goog.require('example.Import'); function getImports() { return {imports: {answer: api.answer}}; }");
        Path runtime = directory.resolve("runtime.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(runtime))) {
            zip.putNextEntry(new ZipEntry("runtime.js"));
            zip.write("goog.module('example.Import'); exports = {answer: () => 42};".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        project.setBuild(build);
        ReactorDependency dependency = new ReactorDependency(project, null) {
            public Collection<Dependency> getDependencies() { return List.of(); }
        };
        Path web = directory.resolve("web");
        BuildConfig config = new BuildConfig(List.of(), List.of(), null, "nested/app.js", web.toString(),
                "BUNDLE", locale.isEmpty() ? Map.of() : Map.of("goog.LOCALE", locale), false, null, false, "ECMASCRIPT_2017", true, "BROWSER", Map.of(),
                List.of(), "WASM", List.of(), runtime.toFile());
        try (BuildContext context = new BuildContext(project, config, null, null)) {
            new WasmFinalTask(dependency, context, LOG) {
                protected TaskOutput input(Collection<Dependency> dependencies, OutputTypes type) {
                    return new TaskOutput(type == OutputTypes.WASM_BUNDLED ? List.of(bundled) : List.of());
                }
            }.process();
        }
        String basename = "app" + (locale.isEmpty() ? "" : "_" + locale);
        String imports = Files.readString(web.resolve("nested/" + basename + ".imports.js"));
        assertTrue(imports.contains("export const getImports = j2cl.wasm.imports;"));
        // HtmlUnit needs ES5; browsers execute the published ES module directly.
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
        Compiler compiler = new Compiler();
        assertTrue(compiler.compile(CommandLineRunner.getDefaultExterns(), List.of(SourceFile.fromCode("imports.js",
                imports.replace("export const getImports", "const getImports"))), options).success);
        try (WebClient client = new WebClient()) {
            HtmlPage page = client.getPage("about:blank");
            page.executeJavaScript(compiler.toSource());
            assertEquals(42, ((Number) page.executeJavaScript("getImports().imports.answer()").getJavaScriptResult()).intValue());
        }
        String loader = Files.readString(web.resolve("nested/" + basename + ".module.js"));
        assertTrue(loader.contains("import { getImports } from './" + basename + ".imports.js'"));
        assertTrue(loader.contains("WebAssembly.instantiateStreaming(response, getImports(), {"));
        assertTrue(loader.contains("new URL('./" + basename + ".wasm', import.meta.url)"));
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
