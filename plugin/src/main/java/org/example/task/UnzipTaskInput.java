package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.util.concurrent.CompletableFuture;

public class UnzipTaskInput extends TaskInput{

    public UnzipTaskInput(Dependency dep, BuildContext buildContext) {
        super(dep, buildContext);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.UNZIPPED_DEPENDENCIES;
    }

    @Override
    public Runnable process() {
        return () -> {
            System.out.println(String.format("Unzipping %s to %s", key(), buildContext.getOutputDirectory()));
        };
    }
}
