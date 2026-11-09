package org.treblereel.j2cl.plugin.task;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.tools.AptPath;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationProcessorOptionsTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearTasks() {
        TaskInput.clearCache();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void processorsReceiveDefinesAndExplicitOverridesInTurbineAndWatch(boolean watch, boolean override) throws Exception {
        Path source = Files.createDirectories(directory.resolve("src"));
        Files.writeString(source.resolve("App.java"), "public class App {}");
        MavenProject project = new MavenProject();
        project.setFile(directory.resolve("pom.xml").toFile());
        project.setGroupId("test");
        project.setArtifactId("app");
        project.setVersion("1");
        project.setDependencyArtifacts(Set.of());
        project.setArtifacts(Set.of());
        Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        build.setSourceDirectory(source.toString());
        project.setBuild(build);
        Path jar = directory.resolve("processor.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Class<?> type : List.of(OptionsProcessor.class, AnnotationProcessorOptionsTest.class)) {
                String name = type.getName().replace('.', '/') + ".class";
                zip.putNextEntry(new ZipEntry(name));
                try (var input = type.getClassLoader().getResourceAsStream(name)) {
                    zip.write(java.util.Objects.requireNonNull(input).readAllBytes());
                }
                zip.closeEntry();
            }
        }
        Map<String, String> arguments = override ? Map.of("goog.LOCALE", "de") : Map.of();
        BuildConfig config = new BuildConfig(List.of(), List.of(), null, "app.js", directory.resolve("web").toString(),
                "SIMPLE_OPTIMIZATIONS", Map.of("goog.LOCALE", "fr", "feature.enabled", true, "limit", 42,
                        "message", "hello world"), false, null, false, "ECMASCRIPT_2017", true, "BROWSER", arguments,
                List.of(new AptPath(jar.toFile(), List.of(OptionsProcessor.class.getName()))));
        ReactorDependency dependency = new ReactorDependency(project, null) {
            public Collection<Dependency> getDependencies() { return List.of(); }
        };
        try (BuildContext context = new BuildContext(project, config, null, null)) {
            context.setWatchMode(watch);
            Path output = new TurbineCodeTask(dependency, context, LOG).runTask().join();
            assertEquals((override ? "de" : "fr") + "|true|42|hello world",
                    Files.readString(output.resolve("options.txt")));
        }
    }

    public static class OptionsProcessor extends AbstractProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() { return Set.of("*"); }
        @Override
        public Set<String> getSupportedOptions() { return Set.of("goog.LOCALE", "feature.enabled", "limit", "message"); }
        @Override
        public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (round.processingOver()) return false;
            Map<String, String> options = processingEnv.getOptions();
            try (var writer = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", "options.txt").openWriter()) {
                writer.write(String.join("|", options.get("goog.LOCALE"), options.get("feature.enabled"),
                        options.get("limit"), options.get("message")));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return false;
        }
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
