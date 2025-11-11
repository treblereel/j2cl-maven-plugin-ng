package org.example.model;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.util.List;

public class Project extends Dependency {

    private final MavenProject project;

    public Project(MavenProject project, ArtifactResolver artifactResolver) {
        super(null, artifactResolver);
        this.project = project;
    }

    @Override
    public List<Dependency> getDependencies() {
        return artifactResolver.getDependencies(project.getGroupId(), project.getArtifactId(), project.getVersion(), "compile");
    }

    public boolean isSourceMapped() {
        return true;
    }

    @Override
    public File bytecodeJar() {
        return new File(project.getBasedir(), "target/classes");
    }

    @Override
    public File sourcesJar() {
        return new File(project.getBasedir(), "src/main/java");
    }

    public String key() {
        return String.format("%s-%s-%s", project.getArtifact().getGroupId(), project.getArtifact().getArtifactId(), project.getArtifact().getVersion());
    }

    @Override
    public String toString() {
        return "Project{" +
                "project=" + project +
                '}';
    }
}
