package org.example.task;

import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;

public class TaskInputFactory {

    public static TaskInput create(Dependency dep, BuildContext buildContext, OutputTypes outputTypes, BuildLog buildLog) {
        return switch (outputTypes) {
            case BYTECODE -> new ByteCodeTask(dep, buildContext, buildLog);
            case UNZIPPED_DEPENDENCIES -> new UnzipTaskInput(dep, buildContext, buildLog);
            case STRIPPED_SOURCES -> new StripSourcesTask(dep, buildContext, buildLog);
            case TRANSPILED_JS -> new J2CLTask(dep, buildContext, buildLog);
            case OPTIMIZED_JS -> new ClosureTask(dep, buildContext, buildLog);
            default -> throw new RuntimeException("Unsupported output type: " + outputTypes);
        };

    }
}
