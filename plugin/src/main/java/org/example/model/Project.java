package org.example.model;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.util.List;

public class Project extends Dependency {

  private final List<Dependency> dependencies;
  private final MavenProject project;

  public Project(MavenProject project, ArtifactResolver artifactResolver, List<Dependency> dependencies) {
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
