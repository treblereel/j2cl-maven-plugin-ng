package org.example.context;

import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.*;
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
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.model.JarDependency;
import org.example.model.ReactorDependency;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ArtifactResolver {

    private final Map<String, org.apache.maven.artifact.Artifact> defaultDependencyReplacement;
    private final MavenProject project;
    private final List<RemoteRepository> remoteRepos;
    private final RepositorySystemSession repoSession;
    private final RepositorySystem repositorySystem;
    private final Set<MavenProject> reactorProjects;
    private final BuildLog logger;
    private final ProjectDependencyGraph dependencyGraph;
    private final ProjectBuilder projectBuilder;
    private final MavenSession mavenSession;

    public ArtifactResolver(MavenProject project, RepositorySystem repoSystem, List<RemoteRepository> remoteRepos, RepositorySystemSession repoSession,
                            MavenSession session, ProjectBuilder projectBuilder, Map<String, org.apache.maven.artifact.Artifact> defaultDependencyReplacement, BuildLog logger) {
        this.project = project;
        this.repositorySystem = repoSystem;
        this.remoteRepos = remoteRepos;
        this.repoSession = repoSession;
        this.mavenSession = session;
        this.reactorProjects = new HashSet<>(session.getAllProjects());
        this.projectBuilder = projectBuilder;
        this.defaultDependencyReplacement = defaultDependencyReplacement;
        this.dependencyGraph = session.getProjectDependencyGraph();
        this.logger = logger;
    }

    public Collection<Dependency> getDependencies(org.apache.maven.artifact.Artifact artifact) {
        ProjectBuildingRequest req =
                new DefaultProjectBuildingRequest(mavenSession.getProjectBuildingRequest());
        req.setResolveDependencies(true);
        req.setRepositorySession(mavenSession.getRepositorySession());
        try {
            MavenProject dependencyProject = projectBuilder.build(artifact, req).getProject();

            return dependencyProject.getArtifacts().stream()
                    .map(a -> {
                        Dependency dependency = new JarDependency(a, this);
                        String key = dependency.groupId() + ":" + dependency.artifactId();
                        if (defaultDependencyReplacement.containsKey(key)) {
                            org.apache.maven.artifact.Artifact replacement = defaultDependencyReplacement.get(key);
                            if (replacement == null) {
                                return null;
                            }
                            var resolved = getDependency(replacement);
                            return new JarDependency(resolved, this);
                        }
                        return new JarDependency(a, this);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (ProjectBuildingException e) {
            throw new RuntimeException("Failed to build project for artifact " + artifact, e);
        }
    }

    public org.apache.maven.artifact.Artifact getDependency(org.apache.maven.artifact.Artifact mavenArtifact) {
        Artifact aetherArtifact = new DefaultArtifact(
                mavenArtifact.getGroupId(),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getClassifier(),
                mavenArtifact.getType(),
                mavenArtifact.getVersion()
        );

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(aetherArtifact);
        request.setRepositories(project.getRemoteProjectRepositories());

        try {
            ArtifactResult result = repositorySystem.resolveArtifact(repoSession, request);
            Artifact resolved = result.getArtifact();

            org.apache.maven.artifact.Artifact resolvedMavenArtifact =
                    new org.apache.maven.artifact.DefaultArtifact(
                            resolved.getGroupId(),
                            resolved.getArtifactId(),
                            resolved.getVersion(),
                            mavenArtifact.getScope(),
                            resolved.getExtension(),
                            resolved.getClassifier(),
                            mavenArtifact.getArtifactHandler()
                    );
            resolvedMavenArtifact.setFile(resolved.getFile());

            return resolvedMavenArtifact;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve " + mavenArtifact, e);
        }
    }

    public List<ReactorDependency> getReactorDependencies(MavenProject project) {
        return dependencyGraph.getUpstreamProjects(project, false)
                .stream()
                .filter(dependency -> dependency.getPackaging().equals("jar"))
                .map(d -> new ReactorDependency(d, this))
                .collect(Collectors.toList());
    }

    public boolean isInReactor(org.apache.maven.artifact.Artifact artifact) {
        return isInReactor(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public boolean isInReactor(Artifact artifact) {
        return isInReactor(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public boolean isInReactor(String groupId, String artifactId, String version) {
        for (MavenProject project : reactorProjects) {
            if (project.getGroupId().equals(groupId)
                    && project.getArtifactId().equals(artifactId)
                    && project.getVersion().equals(version)) {
                return true;
            }
        }
        return false;
    }

    public MavenProject getMavenProject(org.apache.maven.artifact.Artifact artifact) {
        return getMavenProject(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public MavenProject getMavenProject(Artifact artifact) {
        return getMavenProject(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public MavenProject getMavenProject(String groupId, String artifactId, String version) {
        for (MavenProject project : reactorProjects) {
            if (project.getGroupId().equals(groupId)
                    && project.getArtifactId().equals(artifactId)
                    && project.getVersion().equals(version)) {
                return project;
            }
        }
        throw new RuntimeException("Failed to resolve " + groupId + ":" + artifactId + ":" + version);

    }


    public org.eclipse.aether.graph.Dependency getDependencyWithMavenCoords(String coords) {
        ArtifactRequest request = new ArtifactRequest()
                .setRepositories(remoteRepos)
                .setArtifact(new DefaultArtifact(coords));
        try {
            org.eclipse.aether.graph.Dependency dependency =
                    new org.eclipse.aether.graph.Dependency(repositorySystem.resolveArtifact(repoSession, request).getArtifact(),
                            "compile");
            return dependency;
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException("Failed to find artifact " + coords, e);
        }
    }

    public File resolveByteCodeJar(Artifact artifact) {
        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(artifact);
        req.setRepositories(remoteRepos);

        try {
            ArtifactResult res = repositorySystem.resolveArtifact(repoSession, req);
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
            ArtifactResult res = repositorySystem.resolveArtifact(repoSession, req);
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
            return repositorySystem.resolveArtifact(repoSession, request).getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to find artifact " + coords, e);
        }
    }

}
