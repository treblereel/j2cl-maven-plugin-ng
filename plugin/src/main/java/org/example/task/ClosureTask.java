package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

public class ClosureTask extends TaskInput {

    public ClosureTask(Dependency dep, BuildContext buildContext) {
        super(dep, buildContext);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.OPTIMIZED_JS;
    }

    @Override
    public void process() {
        TaskOutput self = input(dep, OutputTypes.TRANSPILED_JS);
        TaskOutput deps = input(dep.getDependencies(), OutputTypes.TRANSPILED_JS);
    }
}
