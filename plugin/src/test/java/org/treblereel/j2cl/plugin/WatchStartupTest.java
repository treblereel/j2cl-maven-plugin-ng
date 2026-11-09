package org.treblereel.j2cl.plugin;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.BuildStatus;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.task.OutputTypes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchStartupTest {
    @TempDir
    Path directory;

    @Test
    void invalidatesChangedUpstreamBeforeInitialPipeline() throws Exception {
        Path source = Files.writeString(directory.resolve("Library.java"), "original");
        ReactorDependency library = module("library", List.of(source), List.of());
        ReactorDependency app = module("app", List.of(), List.of(library));
        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setDirectory(directory.toString());
        project.setBuild(build);
        try (BuildContext context = new BuildContext(project, null, null, null)) {
            Path appMarker = marker(context, app);
            Path libraryMarker = marker(context, library);
            Files.writeString(source, "changed");
            AtomicBoolean ran = new AtomicBoolean();
            new WatchJ2clPluginMojo() {
                void runPipeline(ReactorDependency root, BuildContext ctx, BuildLog log) {
                    assertFalse(Files.exists(appMarker));
                    assertFalse(Files.exists(libraryMarker));
                    ran.set(true);
                }
            }.doWatch(app, context, LOG);
            assertTrue(ran.get());
        }
    }

    private Path marker(BuildContext context, ReactorDependency dependency) throws Exception {
        BuildStatus status = new BuildStatus();
        status.setHash(dependency.getHash());
        status.setOutputTypes(Set.of(OutputTypes.OPTIMIZED_JS));
        Path marker = context.getOutputDirectory().resolve(dependency.key()).resolve(".success");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, new Gson().toJson(status));
        return marker;
    }

    private ReactorDependency module(String key, List<Path> inputs, List<Dependency> dependencies) {
        return new ReactorDependency(null, null) {
            public String key() { return key; }
            public List<Path> getHashPaths() { return inputs; }
            public Collection<Dependency> getDependencies() { return dependencies; }
            public List<Path> getWatchSourcePaths() { return List.of(); }
        };
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
