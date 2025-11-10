package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TaskInput {

    private static final Map<String, CompletableFuture<Path>> tasks = new ConcurrentHashMap<>();

    protected final Dependency dep;
    protected final BuildContext buildContext;

    public TaskInput(Dependency dep, BuildContext buildContext) {
        this.dep = dep;
        this.buildContext = buildContext;
    }

    public abstract OutputTypes getOutputTypes();


    public CompletableFuture<Path> runTask() {
        return tasks.computeIfAbsent(key(), k -> resolve());
    }


    private CompletableFuture<Path> resolve() {
        return CompletableFuture
                .runAsync(process(), buildContext.executor())
                .thenApplyAsync(v -> outputPath(), buildContext.executor());
    }

    public abstract Runnable process();

    protected String key() {
        return String.format("%s-%s", dep.key(), getOutputTypes());
    }

    protected void insureOutputDirectoryExists() {
        buildContext.getOutputDirectory().mkdirs();
        if (!buildContext.getOutputDirectory().exists()) {
            throw new RuntimeException(String.format("Unable to create output directory %s", buildContext.getOutputDirectory()));
        }
        Path taskOutputPath = buildContext.getOutputDirectory().toPath().resolve(key());
        taskOutputPath.toFile().mkdirs();
        if (!taskOutputPath.toFile().exists()) {
            throw new RuntimeException(String.format("Unable to create task output directory %s", taskOutputPath));
        }
    }

    protected List<Path> input(Dependency dependencies, OutputTypes outputTypes) {
        return input(List.of(dependencies), outputTypes);
    }

    protected List<Path> input(List<Dependency> dependencies, OutputTypes outputTypes) {
        List<CompletableFuture<Path>> tasks = dependencies.stream()
                .map(d -> TaskInputFactory.create(d, buildContext, outputTypes).runTask())
                .toList();
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            return tasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
        } catch (CompletionException e) {
            throw new RuntimeException("Input tasks failed", e.getCause());
        }
    }

    protected Path outputPath() {
        return buildContext.getOutputDirectory().toPath().resolve(key());
    }

}
