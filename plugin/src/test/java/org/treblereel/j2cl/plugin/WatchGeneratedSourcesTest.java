package org.treblereel.j2cl.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.ReactorDependency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WatchGeneratedSourcesTest {
    @TempDir
    Path directory;

    @Test
    void watchesGeneratedRootCreationEditsDeletionAndRecreationWithoutWatchingCacheOutputs() throws Exception {
        MavenProject app = project("app");
        MavenProject lib = project("lib");
        MavenProject independent = project("independent");
        app.setDependencyArtifacts(Set.of(artifact(lib), artifact(independent)));
        Path generated = Path.of(lib.getBuild().getDirectory(), "generated-sources/annotations");
        lib.addCompileSourceRoot(generated.toString());
        Path source = generated.resolve("Generated.java");
        MavenSession session = new MavenSession(null, new DefaultRepositorySystemSession(),
                new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());
        session.setAllProjects(List.of(app, lib, independent));
        ArtifactResolver resolver = new ArtifactResolver(app, null, List.of(), null, session, null, Map.of(), null, true);
        ReactorDependency root = new ReactorDependency(app, resolver);
        ReactorDependency child = new ReactorDependency(lib, resolver);
        ReactorDependency other = new ReactorDependency(independent, resolver);
        CountDownLatch ready = new CountDownLatch(1);
        LinkedBlockingQueue<String> builds = new LinkedBlockingQueue<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        BuildLog log = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
                new Class<?>[]{BuildLog.class}, (proxy, method, args) -> {
                    if (method.getName().equals("info") && args[0].toString().startsWith("Watching for changes")) {
                        ready.countDown();
                    }
                    return null;
                });
        try (BuildContext context = new BuildContext(app, null, resolver, null)) {
            Path appMarker = marker(context, root);
            Path libMarker = marker(context, child);
            Path otherMarker = marker(context, other);
            writeMarker(otherMarker);
            WatchJ2clPluginMojo mojo = new WatchJ2clPluginMojo() {
                private boolean initial = true;

                @Override
                void runPipeline(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
                    try {
                        if (!initial) {
                            assertTrue(Files.notExists(appMarker), "App must be invalidated");
                            assertTrue(Files.notExists(libMarker), "Library must be invalidated");
                            assertTrue(Files.exists(otherMarker), "Independent module must retain its cache");
                        }
                        writeMarker(appMarker);
                        writeMarker(libMarker);
                        String contents = Files.isRegularFile(source) ? Files.readString(source) : "missing";
                        if (!initial) builds.add(contents);
                        initial = false;
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                }
            };
            Thread watching = Thread.ofVirtual().start(() -> {
                try {
                    mojo.doWatch(root, context, log);
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS), "Watch did not start");
                Files.writeString(Path.of(lib.getBuild().getDirectory(), "cache-output.txt"), "ignored");
                assertNull(builds.poll(1500, TimeUnit.MILLISECONDS), "Cache output must not trigger a rebuild");
                Files.createDirectories(generated);
                Files.writeString(source, "created");
                awaitBuild(builds, "created", failure);
                Files.writeString(source, "edited");
                awaitBuild(builds, "edited", failure);
                Files.delete(source);
                Files.delete(generated);
                Files.delete(generated.getParent());
                Files.delete(Path.of(lib.getBuild().getDirectory(), "cache-output.txt"));
                Files.delete(Path.of(lib.getBuild().getDirectory()));
                awaitBuild(builds, "missing", failure);
                Files.createDirectories(generated);
                Files.writeString(source, "recreated");
                awaitBuild(builds, "recreated", failure);
                assertNull(failure.get());
            } finally {
                watching.interrupt();
                watching.join(5000);
                assertFalse(watching.isAlive(), "Watch must stop on interruption");
            }
        }
    }

    private void awaitBuild(LinkedBlockingQueue<String> builds, String expected, AtomicReference<Throwable> failure)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            assertNull(failure.get());
            if (expected.equals(builds.poll(200, TimeUnit.MILLISECONDS))) return;
        }
        fail("No watch build observed for " + expected);
    }

    private MavenProject project(String name) throws IOException {
        Path base = Files.createDirectories(directory.resolve(name));
        MavenProject project = new MavenProject();
        project.setFile(base.resolve("pom.xml").toFile());
        project.setGroupId("example");
        project.setArtifactId(name);
        project.setVersion("1");
        Build build = new Build();
        build.setDirectory(Files.createDirectory(base.resolve("target")).toString());
        build.setSourceDirectory(Files.createDirectories(base.resolve("src/main/java")).toString());
        project.setBuild(build);
        project.addCompileSourceRoot(build.getSourceDirectory());
        project.setDependencyArtifacts(Set.of());
        return project;
    }

    private DefaultArtifact artifact(MavenProject project) {
        return new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(),
                "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    private Path marker(BuildContext context, ReactorDependency dependency) {
        return context.getOutputDirectory().resolve(dependency.key()).resolve(".success");
    }

    private void writeMarker(Path marker) {
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "{}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
