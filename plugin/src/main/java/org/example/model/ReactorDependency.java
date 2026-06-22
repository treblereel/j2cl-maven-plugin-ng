package org.example.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.example.context.ArtifactResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ReactorDependency implements Dependency {

    private final MavenProject project;
    private final ArtifactResolver artifactResolver;

    public ReactorDependency(MavenProject project, ArtifactResolver artifactResolver) {
        this.project = project;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public Collection<Dependency> getDependencies() {
        if (project.getDependencyArtifacts() == null) {
            return Set.of();
        }
        return project.getDependencyArtifacts()
                .stream()
                .filter(artifact -> !artifact.getScope().equals("provided"))
                .filter(artifact -> !artifact.getScope().equals("test"))
                .map(artifact -> {
                    if (artifactResolver.isInReactor(artifact)) {
                        return new ReactorDependency(artifactResolver.getMavenProject(artifact), artifactResolver);
                    }
                    return new JarDependency(artifact, artifactResolver);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void setDependency(Artifact artifact) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSourceMapped() {
        return true;
    }

    @Override
    public String key() {
        return String.format("%s-%s-%s", project.getGroupId(), project.getArtifactId(), project.getVersion());
    }

    @Override
    public String groupId() {
        return project.getGroupId();
    }

    @Override
    public String artifactId() {
        return project.getArtifactId();
    }

    @Override
    public String version() {
        return project.getVersion();
    }

    public MavenProject getMavenProject() {
        return project;
    }

    protected ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    public List<Path> getSourcePaths() {
        List<Path> result = new ArrayList<>();
        Path sources = Paths.get(project.getBuild().getSourceDirectory());
        if(Files.exists(sources)) {
            result.add(sources);
        }

        for(Resource resource: project.getBuild().getResources()) {
            Path resourcePath = Paths.get(resource.getDirectory());
            if(Files.exists(resourcePath)) {
                result.add(resourcePath);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ReactorDependency that)) return false;
        return Objects.equals(project, that.project);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(project);
    }
}
