package org.example.context;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
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
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.example.model.Dependency;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtifactResolver {

  private final Map<String, String> defaultDependencyReplacement;
  private final Log log;
  private final List<RemoteRepository> remoteRepos;
  private final RepositorySystemSession repoSession;
  private final RepositorySystem repoSystem;

  private final Set<MavenProject> reactorProjects;

  public ArtifactResolver(RepositorySystem repoSystem, List<RemoteRepository> remoteRepos, RepositorySystemSession repoSession, MavenSession session, Map<String, String> defaultDependencyReplacement, Log log) {
    this.repoSystem = repoSystem;
    this.remoteRepos = remoteRepos;
    this.repoSession = repoSession;
    this.log = log;
    this.reactorProjects = new HashSet<>(session.getAllProjects());
    this.defaultDependencyReplacement = defaultDependencyReplacement;
  }

  public List<Dependency> getDependencies(org.eclipse.aether.graph.Dependency dep) {
    return getDependencies(
            dep.getArtifact().getGroupId(),
            dep.getArtifact().getArtifactId(),
            dep.getArtifact().getVersion(),
            dep.getScope()
    );
  }

  public List<Dependency> getDependencies(String groupId, String artifactId, String version, String scope) {
    String coords = String.format("%s:%s:%s", groupId, artifactId, version);
    var artifact = new DefaultArtifact(coords);
    var request = new CollectRequest();
    request.setRoot(new org.eclipse.aether.graph.Dependency(artifact, scope));
    request.setRepositories(remoteRepos);
    try {
      DependencyNode root = repoSystem.collectDependencies(repoSession, request).getRoot();
      return root.getChildren()
              .stream()
              .filter(d -> {
                String depCoords = String.format("%s:%s",
                        d.getDependency().getArtifact().getGroupId(),
                        d.getDependency().getArtifact().getArtifactId());
                return !(defaultDependencyReplacement.containsKey(depCoords)
                        && defaultDependencyReplacement.get(depCoords) == null);
              })
              .filter(d -> !"provided".equals(d.getDependency().getScope()))
              .filter(d -> !"test".equals(d.getDependency().getScope()))
              .map(d -> {
                Dependency dependency = new Dependency(d.getDependency(), this);
                String depCoords = String.format("%s:%s", dependency.groupId(), dependency.artifactId());
                if (defaultDependencyReplacement.containsKey(depCoords)) {
                  String replacementVersion = defaultDependencyReplacement.get(depCoords);
                  log.info("Replacing dependency " + depCoords + " with " + replacementVersion);
                  org.eclipse.aether.graph.Dependency replacement = getDependencyWithMavenCoords(replacementVersion);
                  dependency.setDependency(replacement);
                }
                return dependency;
              })
              .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  public MavenProject getMavenProject(Artifact artifact) {
    for (MavenProject project : reactorProjects) {
      if (project.getGroupId().equals(artifact.getGroupId())
              && project.getArtifactId().equals(artifact.getArtifactId())
              && project.getVersion().equals(artifact.getVersion())) {
        return project;
      }
    }
    return null;
  }

  public org.eclipse.aether.graph.Dependency getDependencyWithMavenCoords(String coords) {
    ArtifactRequest request = new ArtifactRequest()
            .setRepositories(remoteRepos)
            .setArtifact(new DefaultArtifact(coords));
    try {
      org.eclipse.aether.graph.Dependency dependency =
              new org.eclipse.aether.graph.Dependency(repoSystem.resolveArtifact(repoSession, request).getArtifact(),
                      "compile");
      return dependency;
    } catch (ArtifactResolutionException e) {
      throw new RuntimeException("Failed to find artifact " + coords, e);
    }
  }

  public File resolveByteCodeJar(Artifact artifact) {

    System.out.println("Resolving bytecode jar for artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() +
            " with classifier: " + artifact.getClassifier() + " and extension: " + artifact.getExtension());


    ArtifactRequest req = new ArtifactRequest();
    req.setArtifact(artifact);
    req.setRepositories(remoteRepos);

    try {
      ArtifactResult res = repoSystem.resolveArtifact(repoSession, req);
      Artifact resolved = res.getArtifact();
      File file = resolved.getFile();
      if (file == null || !file.isFile()) {
        throw new IllegalStateException("Resolved artifact has no file: " + resolved);
      }
      return file;
    } catch (ArtifactResolutionException e) {
      throw new RuntimeException("Failed to resolve " + artifact, e);
    }
  }

  public File resolveSourcesJar(Artifact artifact) {
    Artifact target = ("sources".equals(artifact.getClassifier()) && "jar".equals(artifact.getExtension()))
            ? artifact
            : new SubArtifact(artifact, null, "jar");

    ArtifactRequest req = new ArtifactRequest()
            .setArtifact(target)
            .setRepositories(remoteRepos);

    try {
      ArtifactResult res = repoSystem.resolveArtifact(repoSession, req);
      File file = res.getArtifact().getFile();
      if (file == null || !file.isFile()) {
        throw new IllegalStateException("Resolved sources has no file: " + res.getArtifact());
      }
      return file;
    } catch (ArtifactResolutionException e) {
      throw new RuntimeException("Failed to resolve sources for " + artifact, e);
    }
  }

  /**
   * @param coords : expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>"
   * @return a JAR file
   * @throws MojoExecutionException
   */
  public File getJarWithMavenCoords(String coords) throws MojoExecutionException {
    ArtifactRequest request = new ArtifactRequest()
            .setRepositories(remoteRepos)
            .setArtifact(new DefaultArtifact(coords));

    try {
      return repoSystem.resolveArtifact(repoSession, request).getArtifact().getFile();
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException("Failed to find artifact " + coords, e);
    }
  }

    public static org.eclipse.aether.graph.Dependency toAetherDependency(Artifact mavenArtifact) {
        String coords = String.format(
                "%s:%s:%s:%s:%s",
                mavenArtifact.getGroupId(),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getExtension(),
                mavenArtifact.getClassifier(),
                mavenArtifact.getVersion()
        );

        org.eclipse.aether.artifact.Artifact aetherArtifact =
                new DefaultArtifact(coords)
                        .setFile(mavenArtifact.getFile());

        return new org.eclipse.aether.graph.Dependency(aetherArtifact, "compile");
    }
}
