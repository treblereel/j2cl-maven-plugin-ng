package org.treblereel.j2cl.plugin.tools;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMapInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosureCompilerTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"/", "\\"})
    void preloadsNestedZipMapsAndReleasesArchiveBeforeConsumingThem(String separator) throws Exception {
        Path archive = directory.resolve("runtime with spaces.zip");
        String map = "{\"version\":3,\"file\":\"main.js\",\"sources\":[\"../Main.java\"],\"names\":[],\"mappings\":\"AAAA\"}";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("nested/maps/main.js.map"));
            zip.write(map.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setSourceMapOutputPath("app.js.map");
        options.setApplyInputSourceMaps(true);
        options.setResolveSourceMapAnnotations(false);
        ClosureCompiler compiler = new ClosureCompiler();
        compiler.initOptions(options);
        String inputName = archive + "!/nested/main.js";
        String mapName = archive + "!" + separator + "nested" + separator + "maps" + separator + "main.js.map";
        // Leave the original map unreadable so resolving through Closure's ZIP cache would fail.
        compiler.addInputSourceMap(inputName, new SourceMapInput(SourceFile.fromCode(mapName, "invalid map")));
        Files.writeString(archive, "replaced");
        Files.delete(archive);
        assertTrue(compiler.compile(List.of(), List.of(SourceFile.fromCode(inputName, "var value = 42;")), options).success);
        var mapping = compiler.getSourceMapping(inputName, 1, 0);
        assertNotNull(mapping);
        assertEquals((archive + "!/nested/Main.java").replace('\\', '/'), mapping.getOriginalFile());
        assertEquals(1, mapping.getLineNumber());
    }

    @Test
    void resolvesSourceMapAnnotationsInNestedZipEntries() throws Exception {
        Path archive = directory.resolve("runtime.zip");
        String map = "{\"version\":3,\"file\":\"main.js\",\"sources\":[\"../Main.java\"],\"names\":[],\"mappings\":\"AAAA\"}";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("nested/main.js"));
            zip.write("var value = 42;\n//# sourceMappingURL=maps/main.js.map\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("nested/maps/main.js.map"));
            zip.write(map.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var inputs = org.treblereel.j2cl.plugin.utils.FileUtils.readJsZip(archive, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setSourceMapOutputPath("app.js.map");
        options.setApplyInputSourceMaps(true);
        ClosureCompiler compiler = new ClosureCompiler();
        assertTrue(compiler.compile(List.of(), inputs, options).success);
        var mapping = compiler.getSourceMapping(inputs.getFirst().getName(), 1, 0);
        assertNotNull(mapping);
        assertEquals((archive + "!/nested/Main.java").replace('\\', '/'), mapping.getOriginalFile());
        Files.writeString(archive, "replaced");
        Files.delete(archive);
    }

    @Test
    void preservesInlineSourceMapsForJavaScriptLoadedFromZip() throws Exception {
        String inputName = directory.resolve("deleted.zip") + "!/nested/main.js";
        String map = "{\"version\":3,\"file\":\"main.js\",\"sources\":[\"Main.java\"],\"names\":[],\"mappings\":\"AAAA\"}";
        String code = "var value = 42;\n//# sourceMappingURL=data:application/json;base64,"
                + Base64.getEncoder().encodeToString(map.getBytes(StandardCharsets.UTF_8));
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setSourceMapOutputPath("app.js.map");
        options.setApplyInputSourceMaps(true);
        options.setParseInlineSourceMaps(true);
        ClosureCompiler compiler = new ClosureCompiler();
        var source = SourceFile.fromCode(inputName, code);
        assertTrue(compiler.compile(List.of(), List.of(source), options).success);
        var mapping = compiler.getSourceMapping(source.getName(), 1, 0);
        assertNotNull(mapping);
        assertEquals((directory.resolve("deleted.zip") + "!/nested/Main.java").replace('\\', '/'), mapping.getOriginalFile());
    }

    @Test
    void preservesPreloadedMapsWithBangInOrdinaryDirectoryNames() throws Exception {
        String inputName = directory.resolve("project!/main.js").toString().replace('\\', '/');
        String map = "{\"version\":3,\"file\":\"main.js\",\"sources\":[\"Main.java\"],\"names\":[],\"mappings\":\"AAAA\"}";
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setSourceMapOutputPath("app.js.map");
        options.setApplyInputSourceMaps(true);
        ClosureCompiler compiler = new ClosureCompiler();
        compiler.initOptions(options);
        compiler.addInputSourceMap(inputName, new SourceMapInput(SourceFile.fromCode(inputName + ".map", map)));
        assertTrue(compiler.compile(List.of(), List.of(SourceFile.fromCode(inputName, "var value = 42;")), options).success);
        assertNotNull(compiler.getSourceMapping(inputName, 1, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "\\"})
    void reportsMissingArchiveMapWithItsPath(String separator) throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.finish();
        }
        String mapName = archive + "!" + separator + "missing.js.map";
        var error = assertThrows(UncheckedIOException.class, () -> new ClosureCompiler().addInputSourceMap("main.js",
                new SourceMapInput(SourceFile.fromCode(mapName, ""))));
        assertTrue(error.getMessage().contains("missing.js.map"));
        Files.delete(archive);
    }
}
