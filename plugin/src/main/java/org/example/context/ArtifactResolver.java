package org.example.context;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.example.model.Dependency;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtifactResolver {

  private final Log log;
  private final List<RemoteRepository> remoteRepos;
  private final RepositorySystemSession repoSession;
  private final RepositorySystem repoSystem;

  private final Set<MavenProject> reactorProjects;

  public ArtifactResolver(RepositorySystem repoSystem, List<RemoteRepository> remoteRepos, RepositorySystemSession repoSession, MavenSession session, Log log) {
    this.repoSystem = repoSystem;
    this.remoteRepos = remoteRepos;
    this.repoSession = repoSession;
    this.log = log;
    this.reactorProjects = new HashSet<>(session.getAllProjects());

  }

  public List<Dependency> getDependencies(org.apache.maven.model.Dependency dep) throws Exception {
    return getDependencies(
            dep.getGroupId(),
            dep.getArtifactId(),
            dep.getVersion(),
            dep.getScope()
    );
  }

  public List<Dependency> getDependencies(org.eclipse.aether.graph.Dependency dep) throws Exception {
    return getDependencies(
            dep.getArtifact().getGroupId(),
            dep.getArtifact().getArtifactId(),
            dep.getArtifact().getVersion(),
            dep.getScope()
    );
  }

  private List<Dependency> getDependencies(String groupId, String artifactId, String version, String scope) throws Exception {
    String coords = String.format("%s:%s:%s", groupId, artifactId, version);
    var artifact = new DefaultArtifact(coords);
    var request = new CollectRequest();
    request.setRoot(new org.eclipse.aether.graph.Dependency(artifact, scope));
    request.setRepositories(remoteRepos);
    DependencyNode root = repoSystem.collectDependencies(repoSession, request).getRoot();
    return root.getChildren()
            .stream().filter(d -> d.getDependency().getScope() == null || d.getDependency().getScope().equals("compile"))
            .map(dependencyNode -> new Dependency(dependencyNode.getDependency(), this))
            .collect(Collectors.toList());
  }

  public boolean isInReactor(Artifact artifact) {
    for (MavenProject project : reactorProjects) {
      if (project.getGroupId().equals(artifact.getGroupId())
              && project.getArtifactId().equals(artifact.getArtifactId())
              && project.getVersion().equals(artifact.getVersion())) {
        log.info("Artifact " + artifact + " is in reactor, skipping resolution from remote repositories.");
        return true;
      }
    }
    return false;
  }
}
