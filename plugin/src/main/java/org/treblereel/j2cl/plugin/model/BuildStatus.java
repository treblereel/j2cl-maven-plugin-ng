package org.treblereel.j2cl.plugin.model;

import org.treblereel.j2cl.plugin.task.OutputTypes;

import java.util.Set;

public class BuildStatus {

    private String hash;
    private Set<OutputTypes> outputTypes;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Set<OutputTypes> getOutputTypes() {
        return outputTypes;
    }

    public void setOutputTypes(Set<OutputTypes> outputTypes) {
        this.outputTypes = outputTypes;
    }
}
