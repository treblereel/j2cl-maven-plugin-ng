package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.JarDependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCacheRegressionTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearTasks() {
        TaskInput.clearCache();
    }

    @Test
    void replacesCachedJarStagesWhenSnapshotContentsChange() throws Exception {
        Path jar = directory.resolve("lib.jar");
        writeJar(jar, "original");
        JarDependency dependency = dependency(jar);
        try (BuildContext context = context()) {
            Path unzipped = new UnzipTaskInput(dependency, context, LOG).runTask().join();
            Path stripped = new StripSourcesTask(dependency, context, LOG).runTask().join();
            assertTrue(Files.readString(stripped.resolve("Source.java")).contains("original"));
            writeJar(jar, "changed");
            TaskInput.clearCache();
            // Run the downstream stage first, as the real compiler does.
            new StripSourcesTask(dependency, context, LOG).runTask().join();
            assertTrue(Files.readString(unzipped.resolve("Source.java")).contains("changed"));
            assertTrue(Files.readString(stripped.resolve("Source.java")).contains("changed"));
        }
    }

    @Test
    void republishesDeletedAppWithoutRecompilingCachedJavaScript() throws Exception {
        Path jar = directory.resolve("lib.jar");
        writeJar(jar, "original");
        JarDependency dependency = dependency(jar);
        try (BuildContext context = context()) {
            TaskInput optimized = new TaskInput(dependency, context, LOG) {
                public OutputTypes getOutputTypes() { return OutputTypes.OPTIMIZED_JS; }
                public void process() {
                    try {
                        Files.writeString(outputPath().resolve("app.js"), "compiled");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            Path optimizedPath = optimized.runTask().join().resolve("app.js");
            var modified = Files.getLastModifiedTime(optimizedPath);
            new TaskInput(dependency, context, LOG) {
                public OutputTypes getOutputTypes() { return OutputTypes.TRANSPILED_JS; }
                public void process() { }
            }.runTask().join();
            new FinalTask(dependency, context, LOG).runTask().join();
            Path published = Path.of(context.getConfig().webappDirectory()).resolve("app.js");
            Files.delete(published);
            TaskInput.clearCache();
            new FinalTask(dependency, context, LOG).runTask().join();
            assertEquals("compiled", Files.readString(published));
            assertEquals(modified, Files.getLastModifiedTime(optimizedPath));
        }
    }

    private void writeJar(Path jar, String value) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("Source.java"));
            zip.write(("public class Source { public static String value() { return \"" + value + "\"; } }").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private JarDependency dependency(Path jar) {
        var artifact = new DefaultArtifact("test", "library", "1-SNAPSHOT", "compile", "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(jar.toFile());
        return new JarDependency(artifact, null) {
            public Collection<Dependency> getDependencies() { return List.of(); }
        };
    }

    private BuildContext context() {
        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setDirectory(directory.toString());
        project.setBuild(build);
        BuildConfig config = new BuildConfig(List.of(), List.of(), null, "app.js", directory.resolve("web").toString(),
                "SIMPLE_OPTIMIZATIONS", Map.of(), false, null, false, "ECMASCRIPT_2017", true, "BROWSER", Map.of());
        return new BuildContext(project, config, null, null);
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
