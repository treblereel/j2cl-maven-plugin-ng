package org.treblereel.j2cl.plugin.context;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.artifact.Artifact;

/** Re-applies the dependency mediation already performed by Maven for the root project. */
final class DependencyMediation {

    private final Map<String, Artifact> selectedArtifacts;

    DependencyMediation(Collection<Artifact> selectedArtifacts) {
        Map<String, Artifact> artifactsByConflictId = new LinkedHashMap<>();
        if (selectedArtifacts != null) {
            selectedArtifacts.forEach(artifact ->
                    artifactsByConflictId.putIfAbsent(artifact.getDependencyConflictId(), artifact));
        }
        this.selectedArtifacts = Map.copyOf(artifactsByConflictId);
    }

    /**
     * Returns Maven's selected artifact, or {@code null} if this dependency was excluded from the root graph.
     * Dependencies of synthetic replacements are passed through because they do not belong to that graph.
     */
    Artifact mediate(Artifact owner, Artifact candidate) {
        Artifact selected = selectedArtifacts.get(candidate.getDependencyConflictId());
        if (selected != null) {
            return selected;
        }
        return selectedArtifacts.containsKey(owner.getDependencyConflictId()) ? null : candidate;
    }
}
