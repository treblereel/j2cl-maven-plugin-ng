package org.example.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.example.context.ArtifactResolver;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReactorDependency implements Dependency {

    private final MavenProject project;
    private final ArtifactResolver artifactResolver;

    public ReactorDependency(MavenProject project, ArtifactResolver artifactResolver) {
        this.project = project;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public Collection<Dependency> getDependencies() {
        return project.getDependencyArtifacts()
                .stream()
                .filter(artifact -> !artifact.getScope().equals("provided"))
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

    public List<Path> getSourcePaths() {
        return Stream.concat(project.getResources().stream().map(dir -> Path.of(dir.getDirectory())),
                        Stream.of(project.getBuild().getSourceDirectory()).map(Path::of))
                .collect(Collectors.toList());
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
