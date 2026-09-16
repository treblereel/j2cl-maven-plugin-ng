package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.config.ConfigurationFingerprint;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.ReactorDependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigurationCacheTest {
    @TempDir
    Path directory;

    @Test
    void rebuildsClosureOnlyAndSupportsSwitchingBackToEarlierConfiguration() throws IOException {
        AtomicInteger javaRuns = new AtomicInteger();
        AtomicInteger closureRuns = new AtomicInteger();
        run("ADVANCED_OPTIMIZATIONS", javaRuns, closureRuns);
        run("SIMPLE_OPTIMIZATIONS", javaRuns, closureRuns);
        run("SIMPLE_OPTIMIZATIONS", javaRuns, closureRuns);
        run("ADVANCED_OPTIMIZATIONS", javaRuns, closureRuns);
        assertEquals(1, javaRuns.get());
        assertEquals(3, closureRuns.get());
    }

    @Test
    void republishesOptimizedAppAfterBundlePipelineOverwroteWebapp() throws IOException {
        AtomicInteger javaRuns = new AtomicInteger();
        AtomicInteger outputRuns = new AtomicInteger();
        run("ADVANCED_OPTIMIZATIONS", javaRuns, outputRuns, OutputTypes.FINAL_TASK);
        run("BUNDLE_JAR", javaRuns, outputRuns, OutputTypes.BUNDLED_JS_APP);
        run("ADVANCED_OPTIMIZATIONS", javaRuns, outputRuns, OutputTypes.FINAL_TASK);
        assertEquals(1, javaRuns.get());
        assertEquals(3, outputRuns.get());
    }

    @Test
    void mapOrderingDoesNotInvalidateCache() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("a", true);
        first.put("b", "value");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("b", "value");
        second.put("a", true);
        assertEquals(hash(config("SIMPLE_OPTIMIZATIONS", first, Map.of()), OutputTypes.FINAL_TASK),
                hash(config("SIMPLE_OPTIMIZATIONS", second, Map.of()), OutputTypes.FINAL_TASK));
    }

    @Test
    void definesInvalidateClosureButNotJava() {
        BuildConfig first = config("ADVANCED_OPTIMIZATIONS", Map.of("flag", true), Map.of());
        BuildConfig second = config("ADVANCED_OPTIMIZATIONS", Map.of("flag", false), Map.of());
        assertEquals(hash(first, OutputTypes.TRANSPILED_JS), hash(second, OutputTypes.TRANSPILED_JS));
        assertNotEquals(hash(first, OutputTypes.OPTIMIZED_JS), hash(second, OutputTypes.OPTIMIZED_JS));
        assertNotEquals(hash(first, OutputTypes.FINAL_TASK), hash(second, OutputTypes.FINAL_TASK));
    }

    @Test
    void processorArgumentsInvalidateJavaAndDownstreamTasks() {
        BuildConfig first = config("ADVANCED_OPTIMIZATIONS", Map.of(), Map.of("option", "before"));
        BuildConfig second = config("ADVANCED_OPTIMIZATIONS", Map.of(), Map.of("option", "after"));
        assertEquals(hash(first, OutputTypes.UNZIPPED_DEPENDENCIES), hash(second, OutputTypes.UNZIPPED_DEPENDENCIES));
        for (OutputTypes type : List.of(OutputTypes.BYTECODE, OutputTypes.TRANSPILED_JS, OutputTypes.FINAL_TASK)) {
            assertNotEquals(hash(first, type), hash(second, type));
        }
    }

    private String hash(BuildConfig config, OutputTypes type) {
        return ConfigurationFingerprint.hash(config, type, "compiler");
    }

    private BuildConfig config(String level, Map<String, Object> defines, Map<String, String> processors) {
        return new BuildConfig(List.of(), List.of(), null, "app.js", directory.resolve("web").toString(),
                level, defines, false, null, false, "ECMASCRIPT_2017", true, "BROWSER", processors);
    }

    private void run(String level, AtomicInteger javaRuns, AtomicInteger closureRuns) throws IOException {
        run(level, javaRuns, closureRuns, OutputTypes.OPTIMIZED_JS);
    }

    private void run(String level, AtomicInteger javaRuns, AtomicInteger closureRuns, OutputTypes outputType)
            throws IOException {
        TaskInput.clearCache();
        MavenProject project = new MavenProject();
        project.setGroupId("test");
        project.setArtifactId("app");
        project.setVersion("1");
        Build build = new Build();
        build.setDirectory(directory.toString());
        project.setBuild(build);
        Path source = directory.resolve("source.txt");
        Files.writeString(source, "unchanged");
        ReactorDependency dependency = new ReactorDependency(project, null) {
            @Override
            public List<Path> getHashPaths() {
                return List.of(source);
            }
        };
        try (BuildContext context = new BuildContext(project, config(level, Map.of(), Map.of()), null, null)) {
            TaskInput javaTask = new TaskInput(dependency, context, LOG) {
                public OutputTypes getOutputTypes() { return OutputTypes.TRANSPILED_JS; }
                public void process() { javaRuns.incrementAndGet(); }
            };
            new TaskInput(dependency, context, LOG) {
                public OutputTypes getOutputTypes() { return outputType; }
                public void process() {
                    javaTask.runTask().join();
                    closureRuns.incrementAndGet();
                }
            }.runTask().join();
        }
    }

    private static final BuildLog LOG = new BuildLog() {
        public void debug(String message) { }
        public void info(String message) { }
        public void warn(String message) { }
        public void warn(String message, Throwable cause) { }
        public void warn(Throwable cause) { }
        public void error(String message) { }
        public void error(String message, Throwable cause) { }
        public void error(Throwable cause) { }
    };
}
