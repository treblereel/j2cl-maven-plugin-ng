package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TaskInput {

  private static final Map<String, CompletableFuture<Path>> tasks = new ConcurrentHashMap<>();

  protected final Dependency dependency;
  protected final BuildContext buildContext;

  public TaskInput(Dependency dep, BuildContext buildContext) {
    this.dependency = dep;
    this.buildContext = buildContext;
  }

  public abstract OutputTypes getOutputTypes();


  public CompletableFuture<Path> runTask() {
    return tasks.computeIfAbsent(key(), k -> resolve());
  }

  private CompletableFuture<Path> resolve() {
    return CompletableFuture
            .runAsync(doProcess(), buildContext.executor())
            .thenApplyAsync(v -> outputPath(), buildContext.executor())
            .exceptionally(ex -> {
              markFailed();
              System.out.println("Failed to process task " + key() + ": " + ex.getMessage());
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
        try {
          Files.delete(buildContext.getOutputDirectory());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        insureOutputDirectoryExists();
        outputPath().toFile().mkdirs();
        markFailed();
      }
    }
    System.out.println(String.format("Starting  task %s for %s", getOutputTypes().getName(), dependency.key()));
    return true;
  }

  private void afterProcess(double startTime) {
    markSuccess();
    System.out.println(String.format("Completed task %s for %s in %.2f seconds", getOutputTypes().getName(), dependency.key(), (System.currentTimeMillis() - startTime) / 1000.0));
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
    return String.format("%s-%s", dependency.key(), getOutputTypes().getName());
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
    return buildContext.getOutputDirectory().resolve(dependency.key()).resolve(getOutputTypes().getName());
  }

  private void markFailed() {
    Path failedMarker = outputPath().getParent().resolve(".failed");
    if (!Files.exists(failedMarker)) {
      try {
        Files.createFile(failedMarker);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create failed marker file: " + failedMarker, e);
      }
    }
    Path successMarker = outputPath().getParent().resolve(".success");
    if (Files.exists(successMarker)) {
      try {
        Files.delete(successMarker);
      } catch (IOException e) {
        throw new RuntimeException("Unable to delete success marker file: " + successMarker, e);
      }
    }
  }

  protected void markSuccess() {
    Path failedMarker = outputPath().getParent().resolve(".failed");
    if (Files.exists(failedMarker)) {
      try {
        Files.delete(failedMarker);
      } catch (IOException e) {
        throw new RuntimeException("Unable to delete failed marker file: " + failedMarker, e);
      }
    }
    Path successMarker = outputPath().getParent().resolve(".success");
    if (!Files.exists(successMarker)) {
      try {
        Files.createFile(successMarker);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create success marker file: " + successMarker, e);
      }
    }

  }

  protected boolean hasSuccessMarker() {
    Path successMarker = outputPath().getParent().resolve(".success");
    return Files.exists(successMarker);
  }

}
