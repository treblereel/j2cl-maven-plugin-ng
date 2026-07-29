package org.treblereel.j2cl.plugin.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.treblereel.j2cl.plugin.log.BuildLog;

public class BinaryenRunner {

    private final BuildLog log;

    public BinaryenRunner(BuildLog log) {
        this.log = log;
    }

    public static boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("wasm-opt", "--version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public int run(List<String> args, Path workDir) {
        List<String> command = new ArrayList<>();
        command.add("wasm-opt");
        command.addAll(args);

        log.debug("Running wasm-opt: " + String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("wasm-opt exited with code " + exitCode);
            }
            return exitCode;
        } catch (IOException e) {
            throw new RuntimeException("Failed to run wasm-opt", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("wasm-opt was interrupted", e);
        }
    }
}
