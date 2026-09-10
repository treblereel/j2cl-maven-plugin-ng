package org.treblereel.j2cl.plugin.context;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DependencyMediationTest {

    private static final Artifact OWNER = artifact("owner", "1.0");
    private static final Artifact SELECTED = artifact("shared", "2.0");

    @Test
    void usesVersionSelectedByRootProject() {
        DependencyMediation mediation = new DependencyMediation(java.util.List.of(OWNER, SELECTED));

        assertSame(SELECTED, mediation.mediate(OWNER, artifact("shared", "1.0")));
    }

    @Test
    void omitsDependenciesExcludedFromRootProject() {
        DependencyMediation mediation = new DependencyMediation(java.util.List.of(OWNER, SELECTED));

        assertNull(mediation.mediate(OWNER, artifact("excluded", "1.0")));
    }

    @Test
    void preservesDependenciesOfSyntheticReplacement() {
        DependencyMediation mediation = new DependencyMediation(java.util.List.of(OWNER, SELECTED));
        Artifact replacementDependency = artifact("replacement-dependency", "1.0");

        assertSame(replacementDependency,
                mediation.mediate(artifact("synthetic-replacement", "1.0"), replacementDependency));
    }

    private static Artifact artifact(String artifactId, String version) {
        return new DefaultArtifact(
                "example",
                artifactId,
                version,
                Artifact.SCOPE_COMPILE,
                "jar",
                null,
                new DefaultArtifactHandler("jar")
        );
    }
}
