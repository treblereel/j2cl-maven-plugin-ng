package org.treblereel.j2cl.plugin.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.BinaryenRunner;

public class BinaryenTask extends TaskInput {

    private static final List<String> WASM_FEATURE_FLAGS = List.of(
            "--enable-exception-handling",
            "--enable-gc",
            "--enable-reference-types",
            "--enable-sign-ext",
            "--enable-strings",
            "--enable-nontrapping-float-to-int",
            "--enable-bulk-memory",
            "--closed-world",
            "--traps-never-happen"
    );

    public BinaryenTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.WASM_OPTIMIZED;
    }

    @Override
    public void process() {
        if (!BinaryenRunner.isAvailable()) {
            throw new RuntimeException(
                    "wasm-opt (Binaryen) is not found on PATH. " +
                    "Install Binaryen: https://github.com/WebAssembly/binaryen");
        }

        TaskOutput bundled = input(dependency, OutputTypes.WASM_BUNDLED);
        Path moduleWat = bundled.paths().get(0).resolve("module.wat");

        if (!Files.exists(moduleWat)) {
            throw new RuntimeException("module.wat not found in transpiled output: " + moduleWat);
        }

        String baseName = buildContext.getConfig().initialScriptFilename()
                .replace(".js", "");

        BinaryenRunner runner = new BinaryenRunner(logger);
        boolean optimized = isOptimizedBuild();

        if (optimized) {
            runOptimizedStages(runner, moduleWat, baseName);
        } else {
            runDevStage(runner, moduleWat, baseName);
        }
    }

    private boolean isOptimizedBuild() {
        String level = buildContext.getConfig().compilationLevel();
        return "ADVANCED_OPTIMIZATIONS".equalsIgnoreCase(level)
                || "SIMPLE_OPTIMIZATIONS".equalsIgnoreCase(level);
    }

    private void runOptimizedStages(BinaryenRunner runner, Path inputWat, String baseName) {
        Path stage1Output = outputPath().resolve(baseName + ".stage1.wasm");
        Path stage2Output = outputPath().resolve(baseName + ".stage2.wasm");
        Path finalOutput = outputPath().resolve(baseName + ".wasm");
        Path sourceMap = outputPath().resolve(baseName + ".wasm.map");
        Path symbolMap = outputPath().resolve(baseName + ".symbols");
        ensureParentDirs(finalOutput);

        // Stage 1: WAT -> intermediate WASM
        List<String> stage1 = new ArrayList<>(WASM_FEATURE_FLAGS);
        stage1.add("--no-inline=*_<once>_*");
        stage1.add("-O3");
        stage1.add("--cfp-reftest");
        stage1.add("--optimize-j2cl");
        stage1.add("--gufa");
        stage1.add("--unsubtyping");
        stage1.add("-O3");
        stage1.add("--cfp-reftest");
        stage1.add("--optimize-j2cl");
        stage1.add("-O3");
        stage1.add("--cfp-reftest");
        stage1.add("--optimize-j2cl");
        stage1.add(inputWat.toString());
        stage1.add("-o");
        stage1.add(stage1Output.toString());
        runStage(runner, "Stage 1", stage1);

        // Stage 2: further optimization
        List<String> stage2 = new ArrayList<>(WASM_FEATURE_FLAGS);
        stage2.add("--no-inline=*_<once>_*");
        stage2.add("--partial-inlining-ifs=4");
        stage2.add("-fimfs=25");
        stage2.add("--gufa");
        stage2.add("--unsubtyping");
        stage2.add("-O3");
        stage2.add("--cfp-reftest");
        stage2.add("--optimize-j2cl");
        stage2.add("-O3");
        stage2.add("--cfp-reftest");
        stage2.add("--optimize-j2cl");
        stage2.add("-O3");
        stage2.add("--cfp-reftest");
        stage2.add("--optimize-j2cl");
        stage2.add("--gufa");
        stage2.add("--unsubtyping");
        stage2.add("-O3");
        stage2.add("--cfp-reftest");
        stage2.add("--optimize-j2cl");
        stage2.add("-O3");
        stage2.add("--cfp-reftest");
        stage2.add("--optimize-j2cl");
        stage2.add(stage1Output.toString());
        stage2.add("-o");
        stage2.add(stage2Output.toString());
        runStage(runner, "Stage 2", stage2);

        // Stage 3: final optimization + lowering
        List<String> stage3 = new ArrayList<>(WASM_FEATURE_FLAGS);
        stage3.add("--no-full-inline=*_<once>_*");
        stage3.add("--partial-inlining-ifs=4");
        stage3.add("--intrinsic-lowering");
        stage3.add("--gufa");
        stage3.add("--unsubtyping");
        stage3.add("-O3");
        stage3.add("--cfp-reftest");
        stage3.add("--optimize-j2cl");
        stage3.add("-O3");
        stage3.add("--optimize-j2cl");
        stage3.add("--cfp-reftest");
        stage3.add("--type-merging");
        stage3.add("-O3");
        stage3.add("--cfp-reftest");
        stage3.add("--optimize-j2cl");
        stage3.add("--string-lowering-magic-imports");
        stage3.add("--remove-unused-module-elements");
        stage3.add("--reorder-globals");
        stage3.add("--type-finalizing");
        stage3.add("--output-source-map");
        stage3.add(sourceMap.toString());
        stage3.add("--symbolmap=" + symbolMap);
        stage3.add(stage2Output.toString());
        stage3.add("-o");
        stage3.add(finalOutput.toString());
        runStage(runner, "Stage 3", stage3);
    }

    private void runDevStage(BinaryenRunner runner, Path inputWat, String baseName) {
        Path finalOutput = outputPath().resolve(baseName + ".wasm");
        Path sourceMap = outputPath().resolve(baseName + ".wasm.map");
        Path symbolMap = outputPath().resolve(baseName + ".symbols");
        ensureParentDirs(finalOutput);

        List<String> args = new ArrayList<>(WASM_FEATURE_FLAGS);
        args.add("--debuginfo");
        args.add("--intrinsic-lowering");
        args.add("--string-lowering-magic-imports");
        args.add("--remove-unused-module-elements");
        args.add("--output-source-map");
        args.add(sourceMap.toString());
        args.add("--symbolmap=" + symbolMap);
        args.add(inputWat.toString());
        args.add("-o");
        args.add(finalOutput.toString());
        runStage(runner, "Dev", args);
    }

    private static void ensureParentDirs(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create directories for " + path, e);
        }
    }

    private void runStage(BinaryenRunner runner, String stageName, List<String> args) {
        logger.info("Binaryen " + stageName + "...");
        int exitCode = runner.run(args, outputPath());
        if (exitCode != 0) {
            throw new RuntimeException("Binaryen " + stageName + " failed with exit code " + exitCode);
        }
    }
}
