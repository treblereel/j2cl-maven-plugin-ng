package org.example.model;

import org.apache.maven.artifact.Artifact;

import java.util.Collection;

public interface Dependency {

    Collection<Dependency> getDependencies();

    void setDependency(Artifact artifact);

    boolean isSourceMapped();

    String key();

    String groupId();

    String artifactId();

    String version();

}
