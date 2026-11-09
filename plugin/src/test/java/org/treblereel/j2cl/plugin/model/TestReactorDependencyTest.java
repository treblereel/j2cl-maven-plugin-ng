package org.treblereel.j2cl.plugin.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.tools.Hashing;
import org.treblereel.j2cl.plugin.utils.FileUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReactorDependencyTest {
    @TempDir
    Path directory;

    @Test
    void includesGeneratedTestRootsWhenMavenProcessorsAreIgnored() throws Exception {
        MavenProject project = project();
        TestReactorDependency dependency = dependency(project, true);
        Path generated = Path.of(project.getTestCompileSourceRoots().get(1));
        Path target = directory.resolve("unzipped");
        for (Path source : dependency.getSourcePaths()) {
            FileUtils.copyDirectory(source, target);
        }
        assertTrue(dependency.getSourcePaths().contains(generated));
        assertEquals("goog.module('example.Shim');",
                Files.readString(target.resolve("example/Shim.closure.js")));
        assertTrue(Files.exists(target.resolve("example/Shim.shim.js")));
    }

    @Test
    void generatedTestChangesInvalidateSourceHash() throws Exception {
        MavenProject project = project();
        TestReactorDependency dependency = dependency(project, true);
        String before = Hashing.hash(dependency.getHashPaths());
        Path generated = Path.of(project.getTestCompileSourceRoots().get(1));
        Files.writeString(generated.resolve("example/Shim.closure.js"), "changed");
        assertNotEquals(before, Hashing.hash(dependency.getHashPaths()));
    }

    @Test
    void stillExcludesGeneratedRootsWhenPluginRunsProcessors() throws Exception {
        MavenProject project = project();
        TestReactorDependency dependency = new TestReactorDependency(project, null, null);
        assertEquals(List.of(Path.of(project.getTestCompileSourceRoots().getFirst())), dependency.getSourcePaths());
    }

    @Test
    void doesNotIncludeMissingGeneratedRoots() throws Exception {
        MavenProject project = project();
        Path missing = directory.resolve("target/generated-sources/missing");
        project.addTestCompileSourceRoot(missing.toString());
        assertFalse(dependency(project, true).getSourcePaths().contains(missing));
    }

    private TestReactorDependency dependency(MavenProject project, boolean includeGenerated) {
        return new TestReactorDependency(project, null, null, includeGenerated);
    }

    private MavenProject project() throws Exception {
        MavenProject project = new MavenProject();
        project.setBuild(new Build());
        Path sources = Files.createDirectories(directory.resolve("src/test/java"));
        Files.writeString(sources.resolve("Test.java"), "class Test {}");
        Path generated = Files.createDirectories(directory.resolve("target/generated-test-sources/test-annotations"));
        Files.createDirectories(generated.resolve("example"));
        Files.writeString(generated.resolve("example/Shim.closure.js"), "goog.module('example.Shim');");
        Files.writeString(generated.resolve("example/Shim.shim.js"), "export const value = 1;");
        project.addTestCompileSourceRoot(sources.toString());
        project.addTestCompileSourceRoot(generated.toString());
        return project;
    }
}
