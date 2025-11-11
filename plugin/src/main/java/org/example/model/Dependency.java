package org.example.model;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class Dependency {

    private final org.eclipse.aether.graph.Dependency dependency;
    protected final ArtifactResolver artifactResolver;

    public Dependency(org.eclipse.aether.graph.Dependency dependency, ArtifactResolver artifactResolver) {
        this.dependency = dependency;
        this.artifactResolver = artifactResolver;
    }

    public File bytecodeJar() {
        Artifact a = dependency.getArtifact();
        if (a.getFile() != null && a.getFile().isFile()) {
            return a.getFile();
        }

        return artifactResolver.resolveByteCodeJar(dependency.getArtifact());
    }

    public File sourcesJar() {
        return artifactResolver.resolveSourcesJar(dependency.getArtifact());
    }

    public List<Dependency> getDependencies() {
        return artifactResolver.getDependencies(dependency);
    }

    public boolean isSourceMapped() {
        return artifactResolver.isInReactor(dependency.getArtifact());
    }

    public MavenProject asMavenProject() {
        return artifactResolver.getMavenProject(dependency.getArtifact());
    }

    public org.eclipse.aether.graph.Dependency asAetherDependency() {
        return dependency;
    }


    public String key() {
        return String.format("%s-%s-%s", dependency.getArtifact().getGroupId(), dependency.getArtifact().getArtifactId(), dependency.getArtifact().getVersion());
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

    @Override
    public String toString() {
        return "Dependency{" +
                "dependency=" + dependency +
                '}';
    }
}
