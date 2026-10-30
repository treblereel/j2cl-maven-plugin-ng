package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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
                deleteDirectoryRecursive(outputPath());
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
        Path dir = getOutputDirectory();
        try {
            Files.createDirectories(dir);
            Path failedMarker = dir.resolve(".failed");
            if (!Files.exists(failedMarker)) {
                Files.createFile(failedMarker);
            }
            BuildStatus status = readStatus();
            if (status != null) {
                // Preserve successful upstream stages when only this stage's configuration changes.
                status.getOutputTypes().remove(getOutputTypes());
                status.getConfigurationHashes().remove(getOutputTypes());
                // These alternative pipelines publish into the same webapp directory.
                if (getOutputTypes() == OutputTypes.FINAL_TASK || getOutputTypes() == OutputTypes.BUNDLED_JS_APP) {
                    status.getOutputTypes().remove(OutputTypes.FINAL_TASK);
                    status.getOutputTypes().remove(OutputTypes.BUNDLED_JS_APP);
                    status.getConfigurationHashes().remove(OutputTypes.FINAL_TASK);
                    status.getConfigurationHashes().remove(OutputTypes.BUNDLED_JS_APP);
                }
                writeStatus(status);
            } else {
                Files.deleteIfExists(dir.resolve(".success"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to invalidate task " + key(), e);
        }
    }

    protected void markSuccess() {
        try {
            BuildStatus status = readStatus();
            String sourceHash = dependency.isSourceMapped()
                    ? Hashing.hash(((ReactorDependency) dependency).getHashPaths()) : null;
            if (status == null || !java.util.Objects.equals(sourceHash, status.getHash())) {
                status = new BuildStatus();
                status.setHash(sourceHash);
                status.setOutputTypes(new HashSet<>());
                status.setConfigurationHashes(new java.util.EnumMap<>(OutputTypes.class));
            }
            status.getOutputTypes().add(getOutputTypes());
            status.getConfigurationHashes().put(getOutputTypes(), buildContext.configurationHash(getOutputTypes()));
            writeStatus(status);
            Files.deleteIfExists(getOutputDirectory().resolve(".failed"));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write success marker for " + key(), e);
        }
    }

    private BuildStatus readStatus() throws IOException {
        Path marker = getOutputDirectory().resolve(".success");
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try {
            BuildStatus status = gson.fromJson(Files.readString(marker, StandardCharsets.UTF_8), BuildStatus.class);
            // Legacy markers (including empty JAR markers) are rebuilt once.
            return status == null || status.getOutputTypes() == null || status.getConfigurationHashes() == null
                    ? null : status;
        } catch (com.google.gson.JsonParseException e) {
            return null;
        }
    }

    private void writeStatus(BuildStatus status) throws IOException {
        Path marker = getOutputDirectory().resolve(".success");
        Path temporary = Files.createTempFile(getOutputDirectory(), ".success-", ".tmp");
        try {
            Files.writeString(temporary, gson.toJson(status), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, marker, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    protected boolean hasSuccessMarker() {
        try {
            BuildStatus status = readStatus();
            if (status == null || !status.getOutputTypes().contains(getOutputTypes())
                    || !buildContext.configurationHash(getOutputTypes())
                            .equals(status.getConfigurationHashes().get(getOutputTypes()))) {
                return false;
            }
            return !dependency.isSourceMapped()
                    || java.util.Objects.equals(status.getHash(),
                            Hashing.hash(((ReactorDependency) dependency).getHashPaths()));
        } catch (IOException e) {
            throw new RuntimeException("Unable to read success marker for " + key(), e);
        }
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
