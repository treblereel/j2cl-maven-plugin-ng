package org.example.model;

import org.apache.maven.artifact.Artifact;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class JarDependency implements Dependency {

    private final ArtifactResolver artifactResolver;
    private Artifact artifact;

    public JarDependency(Artifact artifact, ArtifactResolver artifactResolver) {
        this.artifact = artifact;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public Collection<Dependency> getDependencies() {
        return artifactResolver.getDependencies(artifact);
    }

    @Override
    public void setDependency(Artifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public boolean isSourceMapped() {
        return false;
    }

    @Override
    public String key() {
        String classifier = artifact.getClassifier();
        if (classifier != null && !classifier.isEmpty()) {
            return String.format("%s-%s-%s-%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), classifier);
        }
        return String.format("%s-%s-%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    @Override
    public String groupId() {
        return artifact.getGroupId();
    }

    @Override
    public String artifactId() {
        return artifact.getArtifactId();
    }

    @Override
    public String version() {
        return artifact.getVersion();
    }

    public File bytecodeJar() {
        return artifact.getFile();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JarDependency that)) return false;
        return Objects.equals(artifact, that.artifact);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(artifact);
    }
}
