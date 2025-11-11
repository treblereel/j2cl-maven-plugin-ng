package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
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
        return String.format("%s-%s", dep.key(), getOutputTypes().getName());
    }

    protected void insureOutputDirectoryExists() {
        buildContext.getOutputDirectory().toFile().mkdirs();
        if (!buildContext.getOutputDirectory().toFile().exists()) {
            throw new RuntimeException(String.format("Unable to create output directory %s", buildContext.getOutputDirectory()));
        }
        Path taskOutputPath = buildContext.getOutputDirectory().resolve(dep.key());
        taskOutputPath.toFile().mkdirs();
        if (!taskOutputPath.toFile().exists()) {
            throw new RuntimeException(String.format("Unable to create task output directory %s", taskOutputPath));
        }
    }

    protected TaskOutput input(Dependency dependencies, OutputTypes outputTypes) {
        return input(List.of(dependencies), outputTypes);
    }

    protected TaskOutput input(List<Dependency> dependencies, OutputTypes outputTypes) {
        List<CompletableFuture<Path>> tasks = dependencies.stream()
                .map(d -> TaskInputFactory.create(d, buildContext, outputTypes).runTask())
                .toList();
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            return new TaskOutput(tasks.stream()
                    .map(CompletableFuture::join)
                    .toList());
        } catch (CompletionException e) {
            throw new RuntimeException("Input tasks failed", e.getCause());
        }
    }

    protected static PathMatcher withSuffix(String suffix) {
        return new PathMatcher() {
            @Override
            public boolean matches(Path p) {
                return p.getFileName().toString().endsWith(suffix);
            }

            @Override
            public String toString() {
                return "Filenames that end with " + suffix;
            }
        };
    }

    protected static PathMatcher filename(String filename) {
        return new PathMatcher() {
            @Override
            public boolean matches(Path p) {
                return p.getFileName().equals(Path.of(filename));
            }

            @Override
            public String toString() {
                return "Filenames that equal " + filename;
            }
        };
    }

    protected Path outputPath() {
        return buildContext.getOutputDirectory().resolve(dep.key()).resolve(getOutputTypes().getName());
    }

}
