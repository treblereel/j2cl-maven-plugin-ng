package org.example.context;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.util.List;

public class ArtifactResolver {

  private final List<RemoteRepository> remoteRepos;
  private final RepositorySystemSession repoSession;
  private final RepositorySystem repoSystem;

  public ArtifactResolver(RepositorySystem repoSystem, List<RemoteRepository> remoteRepos, RepositorySystemSession repoSession) {
    this.repoSystem = repoSystem;
    this.remoteRepos = remoteRepos;
    this.repoSession = repoSession;
  }

  public File resolveArtifact(String groupId, String artifactId, String classifier, String extension, String version) throws Exception {
    Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(artifact);
    artifactRequest.setRepositories(remoteRepos);

    ArtifactResult artifactResult = repoSystem.resolveArtifact(repoSession, artifactRequest);
    return artifactResult.getArtifact().getFile();
  }

}
