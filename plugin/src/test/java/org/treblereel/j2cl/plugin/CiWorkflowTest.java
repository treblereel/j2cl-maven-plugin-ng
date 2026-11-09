package org.treblereel.j2cl.plugin;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiWorkflowTest {
    @Test
    void runsPluginTestsAndTheIntegrationReactor() throws Exception {
        Path base = Path.of(System.getProperty("basedir")).getParent();
        String workflow = Files.readString(base.resolve(".github/workflows/maven.yml"));
        assertTrue(workflow.contains("mvn -B install -pl plugin"));
        assertFalse(workflow.contains("-DskipTests"));
        assertFalse(workflow.contains("-pl tests"), "Selecting the aggregator does not select its modules");
        assertTrue(workflow.contains("-f tests/pom.xml package"));
        assertTrue(workflow.contains("-f samples/single-module/pom.xml package"));
        assertTrue(workflow.contains("releases/download/version_"), "The integration reactor includes WASM builds");
        assertTrue(workflow.contains("-Dtest=BuildOutputIntegrationTest \"-Dj2cl.integration=true\""));
    }
}
