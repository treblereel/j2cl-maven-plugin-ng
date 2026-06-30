package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.BuildStatus;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.tools.Hashing;

import static org.treblereel.j2cl.plugin.utils.FileUtils.deleteDirectoryRecursive;

public abstract class TaskInput {

    private static final Map<String, CompletableFuture<Path>> tasks = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    public static void clearCache() {
        tasks.clear();
    }

    public static void clearCacheForDependency(String dependencyKey) {
        tasks.keySet().removeIf(k -> k.startsWith(dependencyKey + "_"));
    }

    protected final Dependency dependency;
    protected final BuildContext buildContext;
    protected final BuildLog logger;

    public TaskInput(Dependency dep, BuildContext buildContext, BuildLog logger) {
        this.dependency = dep;
        this.buildContext = buildContext;
        this.logger = logger;
    }

    public abstract OutputTypes getOutputTypes();

    public CompletableFuture<Path> runTask() {
        return tasks.computeIfAbsent(key(), k -> resolve());
    }

    private CompletableFuture<Path> resolve() {
        if(buildContext.hasFailed()) {
            CompletableFuture<Path> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Build has already failed, skipping task " + key()));
            return failedFuture;
        }
        return CompletableFuture
                .runAsync(doProcess(), buildContext.executor())
                .thenApplyAsync(v -> outputPath(), buildContext.executor())
                .exceptionally(ex -> {
                    buildContext.markFailed();
                    markFailed();
                    logger.error("Failed to process task " + key() + ": " + ex.getMessage());
                    throw new CompletionException("Task " + key() + " failed", ex);
                });
    }

    private boolean beforeProcess() {
        if (!outputPath().toFile().exists()) {
            insureOutputDirectoryExists();
            outputPath().toFile().mkdirs();
            markFailed();
        } else {
            if (hasSuccessMarker()) {
                return false;
            } else {
                deleteDirectoryRecursive(getOutputDirectory());
                insureOutputDirectoryExists();
                outputPath().toFile().mkdirs();
                markFailed();
            }
        }
        logger.info(String.format("Starting  task %s for %s", getOutputTypes().getName(), dependency.key()));
        return true;
    }

    private void afterProcess(double startTime) {
        markSuccess();
        logger.info(String.format("Completed task %s for %s in %.2f seconds", getOutputTypes().getName(), dependency.key(), (System.currentTimeMillis() - startTime) / 1000.0));
    }

    private Runnable doProcess() {
        return () -> {
            double startTime = System.currentTimeMillis();
            boolean doContinue = beforeProcess();
            if (doContinue) {
                process();
                afterProcess(startTime);
            }
        };
    }


    public abstract void process();

    protected String key() {
        return String.format("%s_%s", dependency.key(), getOutputTypes().getName());
    }

    protected void insureOutputDirectoryExists() {
        buildContext.getOutputDirectory().toFile().mkdirs();
        if (!buildContext.getOutputDirectory().toFile().exists()) {
            throw new RuntimeException(String.format("Unable to create output directory %s", buildContext.getOutputDirectory()));
        }
        Path taskOutputPath = buildContext.getOutputDirectory().resolve(dependency.key());
        taskOutputPath.toFile().mkdirs();
        if (!taskOutputPath.toFile().exists()) {
            throw new RuntimeException(String.format("Unable to create task output directory %s", taskOutputPath));
        }
    }

    protected TaskOutput input(Dependency dependencies, OutputTypes outputTypes) {
        return input(List.of(dependencies), outputTypes);
    }

    protected TaskOutput input(Collection<Dependency> dependencies, OutputTypes outputTypes) {
        List<CompletableFuture<Path>> tasks = dependencies.stream()
                .map(d -> TaskInputFactory.create(d, buildContext, outputTypes, logger).runTask())
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
        return getOutputDirectory().resolve(getOutputTypes().getName());
    }

    protected Path getOutputDirectory() {
        return buildContext.getOutputDirectory().resolve(dependency.key());
    }

    private void markFailed() {
        Path failedMarker = getOutputDirectory().resolve(".failed");
        if (!Files.exists(failedMarker)) {
            try {
                Files.createFile(failedMarker);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create failed marker file: " + failedMarker, e);
            }
        }
        Path successMarker = getOutputDirectory().resolve(".success");
        if (Files.exists(successMarker)) {
            try {
                Files.delete(successMarker);
            } catch (IOException e) {
                throw new RuntimeException("Unable to delete success marker file: " + successMarker, e);
            }
        }
    }

    protected void markSuccess() {
        Path failedMarker = getOutputDirectory().resolve(".failed");
        if (Files.exists(failedMarker)) {
            try {
                Files.delete(failedMarker);
            } catch (IOException e) {
                throw new RuntimeException("Unable to delete failed marker file: " + failedMarker, e);
            }
        }
        Path successMarker = getOutputDirectory().resolve(".success");
        if (!Files.exists(successMarker)) {
            try {
                Path marker = Files.createFile(successMarker);
                if (dependency.isSourceMapped()) {
                    String hash = Hashing.hash(((ReactorDependency) dependency).getSourcePaths());
                    BuildStatus status = new BuildStatus();
                    Set<OutputTypes> outputTypesSet = new HashSet<>();
                    outputTypesSet.add(getOutputTypes());
                    status.setHash(hash);
                    status.setOutputTypes(outputTypesSet);
                    String json = gson.toJson(status);
                    Files.writeString(marker, json);
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to create success marker file: " + successMarker, e);
            }
        } else if (dependency.isSourceMapped()) {
            try {
                String json = Files.readString(successMarker, StandardCharsets.UTF_8);
                BuildStatus status = gson.fromJson(json, BuildStatus.class);
                status.getOutputTypes().add(getOutputTypes());
                String updatedJson = gson.toJson(status);
                Files.writeString(successMarker, updatedJson, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Unable to read/write success marker file: " + successMarker, e);
            }
        }

    }

    protected boolean hasSuccessMarker() {
        Path successMarker = getOutputDirectory().resolve(".success");
        if (Files.exists(successMarker)) {
            if (dependency.isSourceMapped()) {
                try {
                    String json = Files.readString(successMarker);
                    BuildStatus status = gson.fromJson(json, BuildStatus.class);
                    String currentHash = Hashing.hash(((ReactorDependency) dependency).getSourcePaths());
                    return status.getHash().equals(currentHash) && status.getOutputTypes().contains(getOutputTypes());
                } catch (IOException e) {
                    throw new RuntimeException("Unable to read success marker file: " + successMarker, e);
                }

            }
            return true;
        }
        return false;
    }

    protected List<Dependency> getFlattenDependencies() {
        Set<Dependency> processed = new HashSet<>();
        List<Dependency> result = new ArrayList<>();
        Queue<Dependency> queue = new LinkedList<>(dependency.getDependencies());
        while (!queue.isEmpty()) {
            Dependency current = queue.poll();
            if (!processed.contains(current)) {
                result.add(current);
                processed.add(current);
                queue.addAll(current.getDependencies());
            }
        }
        return result;
    }

}
