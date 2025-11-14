package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

public class FinalTask extends TaskInput {

    public FinalTask(Dependency dep, BuildContext buildContext) {
        super(dep, buildContext);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.FINAL_TASK;
    }

    @Override
    public void process() {
        input(dependency, OutputTypes.OPTIMIZED_JS);


    }
}
