package org.treblereel.j2cl.plugin.model;

import java.util.Collection;

import org.apache.maven.artifact.Artifact;

public interface Dependency {

    Collection<Dependency> getDependencies();

    void setDependency(Artifact artifact);

    boolean isSourceMapped();

    String key();

    String groupId();

    String artifactId();

    String version();

}
