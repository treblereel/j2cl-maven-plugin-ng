package org.treblereel.j2cl.plugin.tools;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.turbine.main.Main;

/** Runs Turbine in a separate JVM so its memory-mapped JARs are released on exit. */
public final class TurbineRunner {
    private TurbineRunner() {
    }

    public static void compile(List<String> arguments) throws IOException {
        Set<String> classpath = new LinkedHashSet<>();
        classpath.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = Main.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (URL url : urls.getURLs()) {
                    if (!url.getProtocol().equals("file")) continue;
                    try {
                        classpath.add(Path.of(url.toURI()).toString());
                    } catch (URISyntaxException e) {
                        throw new IOException("Invalid Turbine classpath URL: " + url, e);
                    }
                }
            }
        }
        List<String> command = new ArrayList<>(List.of("-cp", String.join(File.pathSeparator, classpath), Main.class.getName()));
        command.addAll(arguments);
        Path argfile = Files.createTempFile("j2cl-turbine-", ".args");
        try {
            // Java argument files avoid Windows command-line length limits and preserve spaces in APT options.
            Files.write(argfile, command.stream().map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"").toList(),
                    StandardCharsets.UTF_8);
            Path java = Path.of(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java");
            Process process = new ProcessBuilder(java.toString(), "@" + argfile).inheritIO().start();
            try {
                int exit = process.waitFor();
                if (exit != 0) throw new IOException("Turbine compilation failed with exit code " + exit);
            } catch (InterruptedException e) {
                process.destroyForcibly().onExit().join();
                Thread.currentThread().interrupt();
                throw new IOException("Turbine compilation interrupted", e);
            }
        } finally {
            Files.deleteIfExists(argfile);
        }
    }
}
