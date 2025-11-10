package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ByteCodeTask extends TaskInput {

    public ByteCodeTask(Dependency dep, BuildContext buildContext) {
        super(dep, buildContext);
    }

    public OutputTypes getOutputTypes() {
        return OutputTypes.BYTECODE;
    }

    @Override
    public Runnable process() {
        return () -> {
            input(dep, OutputTypes.UNZIPPED_DEPENDENCIES);

            input(dep.getDependencies(), OutputTypes.BYTECODE);

            System.out.println(String.format("Processing %s", key()));
        };
    }



}
