package org.treblereel.j2cl.plugin.model;

import java.util.Map;
import java.util.Set;

import org.treblereel.j2cl.plugin.task.OutputTypes;

public class BuildStatus {

    private String hash;
    private Set<OutputTypes> outputTypes;
    private Map<OutputTypes, String> configurationHashes;

    public Map<OutputTypes, String> getConfigurationHashes() {
        return configurationHashes;
    }

    public void setConfigurationHashes(Map<OutputTypes, String> configurationHashes) {
        this.configurationHashes = configurationHashes;
    }

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
