package org.treblereel.j2cl.plugin.task;

import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;

public class TaskInputFactory {

    public static TaskInput create(Dependency dep, BuildContext buildContext, OutputTypes outputTypes, BuildLog buildLog) {
        return switch (outputTypes) {
            case BYTECODE -> new ByteCodeTask(dep, buildContext, buildLog);
            case UNZIPPED_DEPENDENCIES -> new UnzipTaskInput(dep, buildContext, buildLog);
            case STRIPPED_SOURCES -> new StripSourcesTask(dep, buildContext, buildLog);
            case TRANSPILED_JS -> new J2CLTask(dep, buildContext, buildLog);
            case OPTIMIZED_JS -> new ClosureTask(dep, buildContext, buildLog);
            case BUNDLED_JS -> new ClosureBundleTask(dep, buildContext, buildLog);
            case BUNDLED_JS_APP -> new BundleJarTask(dep, buildContext, buildLog);
            case TRANSPILED_WASM -> new WasmTranspileTask(dep, buildContext, buildLog);
            case WASM_BUNDLED -> new WasmBundlerTask(dep, buildContext, buildLog);
            case WASM_OPTIMIZED -> new BinaryenTask(dep, buildContext, buildLog);
            default -> throw new RuntimeException("Unsupported output type: " + outputTypes);
        };

    }
}
