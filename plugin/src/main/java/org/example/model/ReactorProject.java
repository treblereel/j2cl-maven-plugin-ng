package org.example.model;

import org.apache.maven.project.MavenProject;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ReactorProject extends Dependency {

  private final List<Dependency> dependencies;
  private final MavenProject project;

  public ReactorProject(MavenProject project, ArtifactResolver artifactResolver, List<Dependency> dependencies) {
    super(null, artifactResolver);
    this.project = project;
    this.dependencies = dependencies;
  }

  @Override
  public List<Dependency> getDependencies() {
    return dependencies;
  }

  public boolean isJsZip() {
    return false;
  }

  @Override
  public boolean isSourceMapped() {
    return true;
  }

  @Override
  public MavenProject asMavenProject() {
    return project;
  }

  @Override
  public File bytecodeJar() {
    return new File(project.getBasedir(), "target/classes");
  }

  @Override
  public File sourcesJar() {
    return new File(project.getBasedir(), "src/main/java");
  }

  public List<Path> getSourcePaths() {
    return List.of(asMavenProject().getBasedir().toPath().resolve("src/main/java"),
            asMavenProject().getBasedir().toPath().resolve("src/main/resources"));
  }

  public String key() {
    return String.format("%s-%s-%s", project.getArtifact().getGroupId(), project.getArtifact().getArtifactId(), project.getArtifact().getVersion());
  }

  @Override
  public String toString() {
    return "Project{" +
            "project=" + project +
            '}';
  }
}
