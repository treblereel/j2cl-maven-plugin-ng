package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.context.ReactorCacheInvalidation;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.tools.AptPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationProcessorModeTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void includesMavenGeneratedRootsOnlyInIgnoreMode(boolean ignore) throws Exception {
        MavenProject app = project("app");
        MavenProject lib = project("lib");
        MavenProject leaf = project("leaf");
        app.setDependencyArtifacts(Set.of(artifact(lib)));
        lib.setDependencyArtifacts(Set.of(artifact(leaf)));
        Path generated = Files.createDirectories(directory.resolve("leaf/target/generated-sources/annotations"));
        Files.writeString(generated.resolve("Generated.java"), "class Generated {}");
        leaf.addCompileSourceRoot(generated.toString());
        leaf.addCompileSourceRoot(directory.resolve("missing").toString());
        ArtifactResolver resolver = resolver(ignore, app, lib, leaf);
        try (BuildContext context = context(app, resolver, ignore, List.of())) {
            ReactorDependency root = new ReactorDependency(app, resolver);
            ReactorDependency child = (ReactorDependency) root.getDependencies().iterator().next();
            ReactorDependency transitive = (ReactorDependency) child.getDependencies().iterator().next();
            assertEquals(ignore, transitive.getSourcePaths().contains(generated));
            assertEquals(ignore, transitive.getHashPaths().contains(generated));
            assertEquals(transitive.getSourcePaths(), resolver.getReactorDependencies(app).stream()
                    .filter(d -> d.artifactId().equals("leaf")).findFirst().orElseThrow().getSourcePaths());
            assertEquals(1, transitive.getSourcePaths().stream()
                    .filter(p -> p.toString().equals(leaf.getBuild().getSourceDirectory())).count());
            assertFalse(transitive.getSourcePaths().contains(directory.resolve("missing")));

            for (ReactorDependency dep : List.of(root, child, transitive)) {
                Path stripped = new StripSourcesTask(dep, context, LOG).runTask().join();
                if (dep.equals(transitive)) {
                    assertEquals(ignore, Files.exists(stripped.resolve("Generated.java")));
                    assertEquals(ignore, Files.exists(context.getOutputDirectory().resolve(dep.key())
                            .resolve(OutputTypes.UNZIPPED_DEPENDENCIES.getName()).resolve("Generated.java")));
                }
            }
            assertTrue(ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()).isEmpty());
            Files.writeString(generated.resolve("Generated.java"), "class Generated { int changed; }");
            assertEquals(ignore ? Set.of(root.key(), child.key(), transitive.key()) : Set.of(),
                    ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()));
        } finally {
            TaskInput.clearCache();
        }
    }

    @Test
    void newlyCreatedAndDeletedGeneratedRootsInvalidateConsumers() throws Exception {
        MavenProject app = project("app");
        MavenProject lib = project("lib");
        app.setDependencyArtifacts(Set.of(artifact(lib)));
        Path generated = directory.resolve("lib/target/generated-sources/annotations");
        lib.addCompileSourceRoot(generated.toString());
        ArtifactResolver resolver = resolver(true, app, lib);
        try (BuildContext context = context(app, resolver, true, List.of())) {
            ReactorDependency root = new ReactorDependency(app, resolver);
            ReactorDependency child = (ReactorDependency) root.getDependencies().iterator().next();
            for (ReactorDependency dep : List.of(root, child)) {
                new StripSourcesTask(dep, context, LOG).runTask().join();
            }
            Files.createDirectories(generated);
            Path source = Files.writeString(generated.resolve("Generated.java"), "class Generated {}");
            assertEquals(Set.of(root.key(), child.key()),
                    ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()));
            for (ReactorDependency dep : List.of(root, child)) {
                new StripSourcesTask(dep, context, LOG).runTask().join();
            }
            Files.delete(source);
            Files.delete(generated);
            assertEquals(Set.of(root.key(), child.key()),
                    ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()));
        } finally {
            TaskInput.clearCache();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void providedProcessorsRespectModeAndExplicitExtrasArePreserved(boolean ignore) throws Exception {
        MavenProject app = project("app");
        Path provided = Files.createDirectories(directory.resolve("provided"));
        Path service = provided.resolve("META-INF/services/javax.annotation.processing.Processor");
        Files.createDirectories(service.getParent());
        Files.writeString(service, "example.ProvidedProcessor\n");
        var artifact = new DefaultArtifact("example", "processor", "1", "provided", "jar", null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(provided.toFile());
        app.setDependencyArtifacts(Set.of(artifact));
        // No transitive dependencies are needed by this fixture's provided processor.
        ArtifactResolver resolver = new ArtifactResolver(app, null, List.of(), null,
                session(app), null, Map.of(), LOG, ignore) {
            @Override
            public java.util.Collection<org.treblereel.j2cl.plugin.model.Dependency> getDependencies(
                    org.apache.maven.artifact.Artifact ignored) {
                return List.of();
            }
        };
        Path extra = Files.writeString(directory.resolve("extra.jar"), "fixture");
        try (BuildContext context = context(app, resolver, ignore,
                List.of(new AptPath(extra.toFile(), List.of("example.ExtraProcessor"))))) {
            new ByteCodeTask(new ReactorDependency(app, resolver), context, LOG) {
                @Override
                protected TaskOutput input(java.util.Collection<org.treblereel.j2cl.plugin.model.Dependency> deps,
                                           OutputTypes type) {
                    return new TaskOutput(List.of());
                }

                @Override
                protected void doCompile(List<String> sources, Set<String> modules, List<String> jars,
                                         List<String> names, List<String> opts, File output) {
                    assertEquals(ignore ? List.of("example.ExtraProcessor")
                            : List.of("example.ProvidedProcessor", "example.ExtraProcessor"), names);
                    assertEquals(!ignore, jars.contains(provided.toString()));
                    assertEquals(!ignore, modules.contains(provided.toString()));
                    assertTrue(jars.contains(extra.toString()));
                }
            }.process();
        }
    }

    private MavenProject project(String name) throws Exception {
        MavenProject project = new MavenProject();
        project.setGroupId("example");
        project.setArtifactId(name);
        project.setVersion("1");
        Build build = new Build();
        build.setDirectory(directory.resolve(name + "/target").toString());
        Path sources = Files.createDirectories(directory.resolve(name + "/src/main/java"));
        build.setSourceDirectory(sources.toString());
        project.setBuild(build);
        project.addCompileSourceRoot(sources.toString());
        project.setDependencyArtifacts(Set.of());
        return project;
    }

    private DefaultArtifact artifact(MavenProject project) {
        return new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(),
                "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    private MavenSession session(MavenProject... projects) {
        MavenSession session = new MavenSession(null, new DefaultRepositorySystemSession(),
                new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());
        session.setAllProjects(List.of(projects));
        session.setProjectDependencyGraph((ProjectDependencyGraph) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ProjectDependencyGraph.class},
                (proxy, method, args) -> List.of(projects).subList(1, projects.length)));
        return session;
    }

    private ArtifactResolver resolver(boolean includeCompileSourceRoots, MavenProject... projects) {
        return new ArtifactResolver(projects[0], null, List.of(), null, session(projects), null, Map.of(), LOG, includeCompileSourceRoots);
    }

    private BuildContext context(MavenProject project, ArtifactResolver resolver, boolean ignore, List<AptPath> extras) {
        BuildConfig config = new BuildConfig(List.of(), List.of(), null, "app.js", directory.toString(),
                "ADVANCED_OPTIMIZATIONS", Map.of(), false, null, false, "ECMASCRIPT_2017", true,
                "BROWSER", Map.of(), extras);
        BuildContext context = new BuildContext(project, config, resolver, null);
        context.setIgnoreMavenAnnotationProcessors(ignore);
        return context;
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(
            BuildLog.class.getClassLoader(), new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
