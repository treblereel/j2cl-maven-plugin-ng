package org.example.context;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.example.config.Config;
import org.example.tools.APTProcessors;
import org.example.tools.AptPath;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class BuildContext {


  private final static String CACHE_DIRECTORY = "j2cl-plugin-cache";

  private final ArtifactResolver artifactResolver;
  private final Config config;
  private final MavenProject project;

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
    return APTProcessors.getAPTProcessorPaths(project, artifactResolver, evaluator);
  }

  public void shutdown() {
    executor.shutdown();
  }

  public Path getOutputDirectory() {
    return Path.of(project.getBuild().getDirectory(), CACHE_DIRECTORY);
  }
}
