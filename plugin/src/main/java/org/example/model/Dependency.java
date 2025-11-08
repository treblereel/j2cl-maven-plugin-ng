package org.example.model;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class Dependency {

  private final ArtifactResolver artifactResolver;
  private final org.eclipse.aether.graph.Dependency dependency;
  public Dependency(org.eclipse.aether.graph.Dependency dependency, ArtifactResolver artifactResolver) {
    this.dependency = dependency;
    this.artifactResolver = artifactResolver;
  }

  public File resolve() throws Exception {
    return dependency.getArtifact().getFile();
  }

  private List<Dependency> getDependencies() throws Exception {
    return artifactResolver.getDependencies(dependency);
  }

  public boolean isSourceMapped() {
    return artifactResolver.isInReactor(dependency.getArtifact());
  }


  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    Dependency that = (Dependency) object;
    return Objects.equals(dependency, that.dependency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dependency);
  }
}
