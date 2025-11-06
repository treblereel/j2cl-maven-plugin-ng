package org.example.context;

public class BuildContext {

  private final ArtifactResolver artifactResolver;

  public BuildContext(ArtifactResolver artifactResolver) {
    this.artifactResolver = artifactResolver;
  }


  public ArtifactResolver getArtifactResolver() {
    return artifactResolver;
  }
}
