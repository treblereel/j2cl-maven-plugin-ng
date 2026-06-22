package org.example.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClosureLibrary {

    private ClosureLibrary() {
    }

    private static class Holder {
        private static final Map<String, String> LIBS = loadLibs();
    }

    private static Map<String, String> loadLibs() {
        Map<String, String> libs = new LinkedHashMap<>();
        try {
            libs.put("base.js", readResource("closure/library/base.js"));
            libs.put("long.js", readResource("closure/library/long.js"));
            libs.put("reflect.js", readResource("closure/library/reflect.js"));
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Closure Library resources", e);
        }
        return Collections.unmodifiableMap(libs);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = ClosureLibrary.class
                .getClassLoader()
                .getResourceAsStream(path)) {

            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static Map<String, String> get() {
        return Holder.LIBS;
    }

    public static String getBaseJs() {
        return Holder.LIBS.get("base.js");
    }

    public static Map<String, String> getModuleFiles() {
        Map<String, String> modules = new LinkedHashMap<>();
        Holder.LIBS.forEach((name, content) -> {
            if (!"base.js".equals(name)) {
                modules.put(name, content);
            }
        });
        return modules;
    }
}
