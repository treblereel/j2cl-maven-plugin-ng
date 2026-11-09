package org.treblereel.j2cl.plugin.task;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicResourcesTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearTasks() {
        TaskInput.clearCache();
    }

    @Test
    void publishesScriptsMapsAndPublicResources() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("input"));
        for (String file : List.of("META-INF/resources/site.css", "example/public/index.html", "META-INF/hidden.txt")) {
            Path path = sources.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, file);
        }
        Path transpiled = Files.createDirectories(directory.resolve("transpiled"));
        MavenProject project = new MavenProject();
        project.setFile(directory.resolve("pom.xml").toFile());
        project.setGroupId("test");
        project.setArtifactId("app");
        project.setVersion("1");
        Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        build.setSourceDirectory(transpiled.toString());
        project.setBuild(build);
        ReactorDependency dependency = new ReactorDependency(project, null) {
            public Collection<Dependency> getDependencies() { return List.of(); }
        };
        Path web = directory.resolve("web");
        for (String locale : List.of("")) {
            TaskInput.clearCache();
            Files.writeString(transpiled.resolve("Main.js"), "window.localeResult = '" + locale + "';");
            BuildConfig config = new BuildConfig(List.of(), List.of(), null, "app/app.js", web.toString(),
                    "SIMPLE_OPTIMIZATIONS", locale.isEmpty() ? Map.of() : Map.of("goog.LOCALE", locale),
                    false, null, true, "ECMASCRIPT_2017", true, "BROWSER", Map.of());
            try (BuildContext context = new BuildContext(project, config, null, null)) {
                Path optimized = new ClosureTask(dependency, context, LOG) {
                    protected TaskOutput input(Collection<Dependency> dependencies, OutputTypes type) {
                        if (dependencies.isEmpty()) return new TaskOutput(List.of());
                        return new TaskOutput(List.of(type == OutputTypes.UNZIPPED_DEPENDENCIES ? sources : transpiled));
                    }
                }.runTask().join();
                new FinalTask(dependency, context, LOG) {
                    protected TaskOutput input(Collection<Dependency> dependencies, OutputTypes type) {
                        if (dependencies.isEmpty()) return new TaskOutput(List.of());
                        return new TaskOutput(List.of(type == OutputTypes.OPTIMIZED_JS ? optimized : transpiled));
                    }
                }.runTask().join();
            }
        }
        for (String locale : List.of("")) {
            String suffix = locale.isEmpty() ? "" : "_" + locale;
            String mapUrl = "sources" + suffix + "/app" + suffix + ".js.map";
            assertTrue(Files.readString(web.resolve("app/app" + suffix + ".js"))
                    .contains("sourceMappingURL=" + mapUrl));
            Path map = web.resolve("app").resolve(mapUrl);
            var sourceNames = JsonParser.parseString(Files.readString(map)).getAsJsonObject().getAsJsonArray("sources");
            for (var name : sourceNames) {
                assertTrue(Files.isRegularFile(map.getParent().resolve(name.getAsString())));
            }
            assertEquals("window.localeResult = '" + locale + "';",
                    Files.readString(map.getParent().resolve("Main.js")));
        }
        assertEquals("META-INF/resources/site.css", Files.readString(web.resolve("app/site.css")));
        assertEquals("example/public/index.html", Files.readString(web.resolve("app/index.html")));
        assertFalse(Files.exists(web.resolve("app/META-INF")));
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
