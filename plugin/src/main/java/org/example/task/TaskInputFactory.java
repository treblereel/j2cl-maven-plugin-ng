package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

public class TaskInputFactory {


    public static TaskInput create(Dependency dep, BuildContext buildContext, OutputTypes outputTypes) {
        if (outputTypes == OutputTypes.BYTECODE) {
            return new ByteCodeTask(dep, buildContext);
        } else if (outputTypes == OutputTypes.UNZIPPED_DEPENDENCIES) {
            return new UnzipTaskInput(dep, buildContext);
        }
        throw new IllegalArgumentException("Unsupported output type: " + outputTypes);
    }
}
