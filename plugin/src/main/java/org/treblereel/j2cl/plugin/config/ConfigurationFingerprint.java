package org.treblereel.j2cl.plugin.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import org.treblereel.j2cl.plugin.Versions;
import org.treblereel.j2cl.plugin.task.OutputTypes;

/** Includes upstream compiler settings so a cached final task cannot hide a configuration change. */
public final class ConfigurationFingerprint {
    private ConfigurationFingerprint() {
    }

    public static String hash(Config config, OutputTypes type, String compilerConfiguration) {
        Map<String, Object> values = new TreeMap<>();
        values.put("format", 1);
        values.put("stage", type.name());
        values.put("j2cl", Versions.J2CL_VERSION);
        values.put("plugin", Versions.PLUGIN_VERSION);
        if (type != OutputTypes.UNZIPPED_DEPENDENCIES && type != OutputTypes.STRIPPED_SOURCES) {
            values.put("backend", config.backend());
            values.put("classpath", paths(config.getExtraClasspath()));
            values.put("bootstrapClasspath", path(config.getBootstrapClasspath()));
            values.put("processorArguments", new TreeMap<>(config.annotationProcessorsArgs()));
            values.put("processors", config.getExtraAnnotationProcessors().stream()
                    .map(p -> List.of(path(p.annotationProcessorFile()), p.annotationProcessorName())).toList());
            values.put("mavenCompiler", compilerConfiguration);
        }
        switch (type) {
            case UNZIPPED_DEPENDENCIES, STRIPPED_SOURCES, BYTECODE, TRANSPILED_JS -> { }
            case TRANSPILED_WASM -> values.put("wasmEntryPoints", config.wasmEntryPoints());
            default -> {
                values.put("closure", Versions.CLOSURE_VERSION);
                values.put("compilationLevel", config.compilationLevel());
                values.put("defines", new TreeMap<>(config.defines()));
                values.put("rewritePolyfills", config.rewritePolyfills());
                values.put("languageOut", config.languageOut());
                values.put("checkAssertions", config.checkAssertions());
                values.put("env", config.env());
                values.put("sourceMaps", config.enableSourcemaps());
                values.put("jsZip", paths(config.getJsZip()));
                values.put("wasmEntryPoints", config.wasmEntryPoints());
                values.put("wasmJreJsZip", path(config.getWasmJreJsZip()));
                values.put("translations", config.translationsFile());
                values.put("scriptFilename", config.initialScriptFilename());
                if (type == OutputTypes.FINAL_TASK || type == OutputTypes.BUNDLED_JS_APP) {
                    values.put("webappDirectory", path(new File(config.webappDirectory())));
                }
            }
        }
        return com.google.common.hash.Hashing.sha256()
                .hashString(new Gson().toJson(values), StandardCharsets.UTF_8).toString();
    }

    private static List<String> paths(List<File> files) {
        return files.stream().map(ConfigurationFingerprint::path).toList();
    }

    private static String path(File file) {
        return file == null ? "" : file.toPath().toAbsolutePath().normalize().toString();
    }
}
