package org.example.context;

import org.apache.maven.project.MavenProject;
import org.example.config.Config;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuildContext {


  private final static String CACHE_DIRECTORY = "j2cl-plugin-cache";

  private final ArtifactResolver artifactResolver;
  private final Config config;
  private final MavenProject project;

  public BuildContext(MavenProject project, Config config, ArtifactResolver artifactResolver) {
    this.artifactResolver = artifactResolver;
    this.project = project;
    this.config = config;
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

  public void shutdown() {
    executor.shutdown();
  }

  public Path getOutputDirectory() {
    return Path.of(project.getBuild().getDirectory(), CACHE_DIRECTORY);
  }
}
