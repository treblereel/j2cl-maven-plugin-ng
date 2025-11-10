package org.example.context;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuildContext {

    private final ArtifactResolver artifactResolver;
    private final MavenProject project;

    public BuildContext(MavenProject project, ArtifactResolver artifactResolver) {
        this.artifactResolver = artifactResolver;
        this.project = project;
    }


    public ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutorService executor() {
        return executor;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public File getOutputDirectory() {
        return new File(project.getBuild().getDirectory());
    }
}
