package org.treblereel.j2cl.plugin.context;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.treblereel.j2cl.plugin.config.Config;
import org.treblereel.j2cl.plugin.tools.APTProcessors;
import org.treblereel.j2cl.plugin.tools.AptPath;

public class BuildContext implements AutoCloseable {

  private final static String CACHE_DIRECTORY = "j2cl-plugin-cache";

  private final ArtifactResolver artifactResolver;
  private final Config config;
  private final MavenProject project;

  private final AtomicBoolean failed = new AtomicBoolean(false);

  private final PluginParameterExpressionEvaluator evaluator;

  public BuildContext(MavenProject project, Config config, ArtifactResolver artifactResolver, PluginParameterExpressionEvaluator evaluator) {
    this.artifactResolver = artifactResolver;
    this.project = project;
    this.config = config;
    this.evaluator = evaluator;
  }


  public ArtifactResolver getArtifactResolver() {
    return artifactResolver;
  }

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public ExecutorService executor() {
    return executor;
  }

  public Config getConfig() {
    return config;
  }

  public List<AptPath> getAPTProcessorPaths(MavenProject project) {
    if (ignoreMavenAnnotationProcessors) {
      return List.of();
    }
    return APTProcessors.getAPTProcessorPaths(project, artifactResolver, evaluator);
  }

  public void setIgnoreMavenAnnotationProcessors(boolean ignore) {
    this.ignoreMavenAnnotationProcessors = ignore;
  }

  public void markFailed() {
    failed.set(true);
  }

  public void resetFailed() {
    failed.set(false);
  }

  public boolean hasFailed() {
    return failed.get();
  }

  @Override
  public void close() {
    executor.shutdown();
  }

  public Path getOutputDirectory() {
    return Path.of(project.getBuild().getDirectory(), CACHE_DIRECTORY);
  }

  public java.io.File getProjectBaseDir() {
    return project.getBasedir();
  }

  private boolean ignoreMavenAnnotationProcessors;
  private boolean watchMode;

  public boolean isWatchMode() {
    return watchMode;
  }

  public void setWatchMode(boolean watchMode) {
    this.watchMode = watchMode;
  }

  private final List<Path> additionalXtbSearchPaths = new ArrayList<>();

  public List<Path> getAdditionalXtbSearchPaths() {
    return additionalXtbSearchPaths;
  }

  public void addXtbSearchPath(Path path) {
    if (!additionalXtbSearchPaths.contains(path)) {
      additionalXtbSearchPaths.add(path);
    }
  }
}
