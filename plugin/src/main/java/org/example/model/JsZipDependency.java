package org.example.model;

import org.eclipse.aether.artifact.Artifact;
import org.example.context.ArtifactResolver;

import java.io.File;

public class JsZipDependency extends Dependency {

    private final Artifact jszipArtifact;

    public JsZipDependency(org.eclipse.aether.graph.Dependency dependency, Artifact jszipArtifact, ArtifactResolver artifactResolver) {
        super(dependency, artifactResolver);
        this.jszipArtifact = jszipArtifact;
    }

    @Override
    public File bytecodeJar() {
        System.out.println("ZZ " + jszipArtifact.getFile());
        return jszipArtifact.getFile();
    }

    public boolean isJsZip() {
        return true;
    }
}
