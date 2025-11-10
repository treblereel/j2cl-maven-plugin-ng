package org.example.model;

import org.apache.maven.project.MavenProject;
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
        return false;
    }

    @Override
    public File resolve() {
        return new File(project.getBasedir(), "target/classes");
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
