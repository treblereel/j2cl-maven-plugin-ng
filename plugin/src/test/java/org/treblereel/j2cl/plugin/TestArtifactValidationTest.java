package org.treblereel.j2cl.plugin;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestArtifactValidationTest {

    @TempDir
    Path directory;

    @Test
    void missingSuiteFailsEvenWhenStaleBrowserArtifactsExist() throws Exception {
        Files.writeString(directory.resolve("ExampleTest.html"), "stale HTML");
        Files.writeString(directory.resolve("ExampleTest.js"), "stale JavaScript");
        assertMissingArtifact("ExampleTest.testsuite");
    }

    @Test
    void missingHtmlFails() {
        assertMissingArtifact("ExampleTest.html");
    }

    @Test
    void missingJavaScriptFails() {
        assertMissingArtifact("ExampleTest.js");
    }

    @Test
    void directoryIsNotATestArtifact() throws Exception {
        Files.createDirectory(directory.resolve("ExampleTest.testsuite"));
        assertMissingArtifact("ExampleTest.testsuite");
    }

    @Test
    void existingArtifactIsAccepted() throws Exception {
        Path file = Files.writeString(directory.resolve("ExampleTest.testsuite"), "test suite");
        assertDoesNotThrow(() -> TestJ2clPluginMojo.requireTestArtifact(file, "example.ExampleTest"));
    }

    private void assertMissingArtifact(String filename) {
        Path file = directory.resolve(filename);
        MojoExecutionException error = assertThrows(MojoExecutionException.class,
                () -> TestJ2clPluginMojo.requireTestArtifact(file, "example.ExampleTest"));
        assertTrue(error.getMessage().contains("example.ExampleTest"));
        assertTrue(error.getMessage().contains(file.toString()));
    }
}
