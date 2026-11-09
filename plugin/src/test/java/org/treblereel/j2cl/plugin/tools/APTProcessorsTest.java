package org.treblereel.j2cl.plugin.tools;

import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class APTProcessorsTest {
    @Test
    void selectsProcessorsForTheRequestedCompilationRegardlessOfExecutionOrder() {
        for (boolean reversed : List.of(false, true)) {
            MavenProject project = new MavenProject();
            project.setBuild(new Build());
            Plugin plugin = new Plugin();
            plugin.setConfiguration(config("shared"));
            var compile = execution("compile", "main");
            var tests = execution("testCompile", "tests");
            plugin.setExecutions(reversed ? List.of(tests, compile) : List.of(compile, tests));
            assertEquals("main", processor(APTProcessors.getMergedCompilerConfig(project, plugin, false)));
            assertEquals("tests", processor(APTProcessors.getMergedCompilerConfig(project, plugin, true)));
            assertEquals("shared", processor((Xpp3Dom) plugin.getConfiguration()));
        }
    }

    @Test
    void defaultLifecycleExecutionsOverrideBaseWithoutExplicitGoals() {
        MavenProject project = new MavenProject();
        project.setBuild(new Build());
        Plugin plugin = new Plugin();
        var compile = execution("compile", "main");
        compile.setId("default-compile");
        compile.setGoals(List.of());
        plugin.addExecution(compile);
        assertEquals("main", processor(APTProcessors.getMergedCompilerConfig(project, plugin, false)));
        assertNull(APTProcessors.getMergedCompilerConfig(project, plugin, true).getChild("annotationProcessorPaths"));
    }

    private PluginExecution execution(String goal, String processor) {
        PluginExecution execution = new PluginExecution();
        execution.setId(goal);
        execution.addGoal(goal);
        execution.setConfiguration(config(processor));
        return execution;
    }

    private Xpp3Dom config(String name) {
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom paths = new Xpp3Dom("annotationProcessorPaths");
        Xpp3Dom path = new Xpp3Dom("path");
        Xpp3Dom artifact = new Xpp3Dom("artifactId");
        artifact.setValue(name);
        path.addChild(artifact);
        paths.addChild(path);
        config.addChild(paths);
        return config;
    }

    private String processor(Xpp3Dom config) {
        return config.getChild("annotationProcessorPaths").getChild("path").getChild("artifactId").getValue();
    }
}
