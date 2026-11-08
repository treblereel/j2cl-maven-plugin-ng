package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.context.ReactorCacheInvalidation;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.JarDependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.model.TestReactorDependency;
import org.treblereel.j2cl.plugin.tools.AptPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurbineResourcesTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearTasks() {
        TaskInput.clearCache();
    }

    @ParameterizedTest
    @ValueSource(strings = {"META-INF/module.j2cl.xml", "Emulation.gwt.xml"})
    void resourceJavaAndNativeInputsAreNotReportedAsGeneratedOutputs(String module) throws Exception {
        MavenProject app = project("app");
        Path resources = Files.createDirectories(app.getBasedir().toPath().resolve("resources"));
        Files.createDirectories(resources.resolve(module).getParent());
        resource(app, false, "resources", module, "<module><super-source path=\"emul\"/></module>");
        Path emulation = Files.createDirectories(resources.resolve("emul/java/lang"));
        String java = "package java.lang; public class NoSuchMethodException {}";
        Files.writeString(emulation.resolve("NoSuchMethodException.java"), java);
        Files.writeString(emulation.resolve("NoSuchMethodException.native.js"), "// native input");
        MavenProject lib = project("lib");
        resource(lib, false, "resources", "LibraryResource.java", "// dependency resource");
        Artifact provided = artifact("lib", null, "provided", null);
        app.setDependencyArtifacts(Set.of(provided));
        app.setArtifacts(Set.of(provided));
        ArtifactResolver resolver = resolver(app, lib);
        try (BuildContext context = context(app, resolver, "example.app.MainInput",
                "emul/java/lang/NoSuchMethodException.java=" + java
                        + "|LibraryResource.java=// dependency resource", "", true)) {
            Path bytecode = new TurbineCodeTask(new ReactorDependency(app, resolver), context, LOG).runTask().join();
            TaskOutput outputs = new TaskOutput(List.of(bytecode));
            assertEquals(List.of(Path.of("example/GeneratedResourceProof.java")),
                    outputs.filter(ByteCodeTask.JAVA_SOURCES).files().stream().map(FileEntry::getSourcePath).toList());
            assertEquals(List.of(Path.of("example/GeneratedResourceProof.native.js")),
                    outputs.filter(J2CLTask.NATIVE_JS_SOURCES).files().stream().map(FileEntry::getSourcePath).toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"compile", "provided"})
    void turbineReadsFilteredProjectReactorAndOriginalJarResources(String scope) throws Exception {
        MavenProject lib = project("lib");
        Resource library = resource(lib, false, "library", "library.txt", "${greeting}");
        library.setTargetPath("templates");
        lib.getProperties().setProperty("greeting", "library");
        resource(lib, true, "test-resources", "library-test-only.txt", "hidden");
        MavenProject app = project("app");
        resource(app, false, "main-resources", "main.txt", "${greeting}");
        resource(app, true, "test-resources", "test-only.txt", "hidden");
        app.getProperties().setProperty("greeting", "application");
        Path jar = jar("packaged");
        Artifact external = artifact("external", jar, "compile", null);
        Artifact reactor = artifact("lib", null, scope, null);
        app.setDependencyArtifacts(new LinkedHashSet<>(List.of(reactor, external)));
        app.setArtifacts(new LinkedHashSet<>(List.of(reactor, external)));
        ArtifactResolver resolver = resolver(app, lib);
        ReactorDependency root = new ReactorDependency(app, resolver);
        try (BuildContext context = context(app, resolver, "example.app.MainInput",
                "main.txt=application|templates/library.txt=library|jar.txt=packaged", "test-only.txt|library-test-only.txt")) {
            assertTrue(Files.isRegularFile(new TurbineCodeTask(root, context, LOG).runTask().join().resolve("output.jar")));
            lib.getProperties().setProperty("greeting", "updated library");
            assertEquals(scope.equals("provided") ? Set.of(root.key())
                    : Set.of(root.key(), new ReactorDependency(lib, resolver).key()),
                    ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()));
        }
        try (BuildContext context = context(app, resolver, "example.app.MainInput",
                "main.txt=application|templates/library.txt=updated library|jar.txt=packaged", "test-only.txt|library-test-only.txt")) {
            new TurbineCodeTask(root, context, LOG).runTask().join();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testResourcesEnrichMainResourcesAndOverrideMatchingPaths(boolean watch) throws Exception {
        MavenProject app = project("app");
        resource(app, false, "main-resources", "main.txt", "main");
        Resource main = resource(app, false, "more-main-resources", "shared.txt", "main version");
        resource(app, true, "test-resources", "shared.txt", "test version");
        resource(app, true, "more-test-resources", "test.txt", "test");
        Artifact testJar = artifact("external", jar("test dependency"), "test", null);
        app.setDependencyArtifacts(Set.of(testJar));
        app.setArtifacts(Set.of(testJar));
        ArtifactResolver resolver = resolver(app);
        ReactorDependency mainDep = new ReactorDependency(app, resolver);
        try (BuildContext context = context(app, resolver, "example.app.MainInput", "main.txt=main", "test.txt|jar.txt")) {
            context.setWatchMode(watch);
            new TurbineCodeTask(mainDep, context, LOG).runTask().join();
        }
        TaskInput.clearCache();
        TestReactorDependency tests = new TestReactorDependency(app, resolver, mainDep);
        try (BuildContext context = context(app, resolver, "example.app.TestInput",
                "main.txt=main|shared.txt=test version|test.txt=test|jar.txt=test dependency", "")) {
            context.setWatchMode(watch);
            new TurbineCodeTask(tests, context, LOG).runTask().join();
            Path cachedMain = context.getOutputDirectory().resolve(mainDep.key()).resolve(ByteCodeTask.APT_RESOURCES);
            assertFalse(Files.exists(cachedMain.resolve("test.txt")));
            assertEquals("main version", Files.readString(cachedMain.resolve("shared.txt")));
            assertTrue(tests.getHashPaths().contains(Path.of(main.getDirectory())));
            String productionHash = mainDep.getHash();
            String testsHash = tests.getHash();
            Files.writeString(app.getBasedir().toPath().resolve("more-test-resources/test.txt"), "changed test resource");
            assertEquals(productionHash, mainDep.getHash(), "Test-only edits must retain main inputs");
            assertNotEquals(testsHash, tests.getHash());
        }
    }

    @Test
    void selectionFilterFileAndJarChangesInvalidateAndRemoveStaleResources() throws Exception {
        MavenProject app = project("app");
        Resource resource = resource(app, false, "resources", "selected.txt", "${filtered}");
        Files.writeString(Path.of(resource.getDirectory(), "excluded.txt"), "must not leak");
        resource.addInclude("*.txt");
        resource.addExclude("excluded.txt");
        Path filters = Files.writeString(directory.resolve("app/filters.properties"), "filtered=before\n");
        app.getBuild().addFilter(filters.toString());
        Path jar = jar("before");
        Artifact external = artifact("external", jar, "compile", null);
        app.setDependencyArtifacts(Set.of(external));
        app.setArtifacts(Set.of(external));
        ArtifactResolver resolver = resolver(app);
        ReactorDependency root = new ReactorDependency(app, resolver);
        try (BuildContext context = context(app, resolver, "example.app.MainInput",
                "selected.txt=before|jar.txt=before", "excluded.txt")) {
            new TurbineCodeTask(root, context, LOG).runTask().join();
            assertTrue(ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()).isEmpty());
            String before = root.getHash();
            Files.writeString(filters, "filtered=after\n");
            assertNotEquals(before, root.getHash(), "Filter-file contents must invalidate APT");
            before = root.getHash();
            jar("after");
            assertNotEquals(before, root.getHash(), "Same-coordinate JAR replacement must invalidate APT");
            before = root.getHash();
            resource.addExclude("selected.txt");
            assertNotEquals(before, root.getHash(), "Effective resource selection must invalidate APT");
            before = root.getHash();
            Files.writeString(app.getFile().toPath(), "<project><name>Changed filtering metadata</name></project>");
            assertNotEquals(before, root.getHash(), "POM metadata used by filtering must invalidate APT");
            assertEquals(Set.of(root.key()), ReactorCacheInvalidation.invalidate(root, context.getOutputDirectory()));
        }
        try (BuildContext context = context(app, resolver, "example.app.MainInput", "jar.txt=after", "selected.txt|excluded.txt")) {
            new TurbineCodeTask(root, context, LOG).runTask().join();
            Path staged = context.getOutputDirectory().resolve(root.key()).resolve(ByteCodeTask.APT_RESOURCES);
            assertFalse(Files.exists(staged.resolve("selected.txt")));
            assertTrue(root.getWatchSourcePaths().contains(filters));
        }
    }

    @Test
    void usesEffectiveResourcePluginConfigurationForMainAndTest() throws Exception {
        MavenProject app = project("app");
        app.getProperties().setProperty("greeting", "filtered");
        resource(app, false, "main-resources", "main.txt", "@greeting@");
        resource(app, true, "test-resources", "test.txt", "#greeting#");
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-resources-plugin");
        plugin.setConfiguration(org.codehaus.plexus.util.xml.Xpp3DomBuilder.build(
                new java.io.StringReader("<configuration><useDefaultDelimiters>false</useDefaultDelimiters>"
                        + "<delimiters><delimiter>@</delimiter></delimiters></configuration>")));
        PluginExecution execution = new PluginExecution();
        execution.setId("default-testResources");
        execution.setConfiguration(org.codehaus.plexus.util.xml.Xpp3DomBuilder.build(
                new java.io.StringReader("<configuration><delimiters><delimiter>#</delimiter></delimiters></configuration>")));
        plugin.addExecution(execution);
        app.getBuild().addPlugin(plugin);
        ArtifactResolver resolver = resolver(app);
        TestReactorDependency root = new TestReactorDependency(app, resolver, new ReactorDependency(app, resolver));
        try (BuildContext context = context(app, resolver, "example.app.TestInput", "main.txt=filtered|test.txt=filtered", "")) {
            new TurbineCodeTask(root, context, LOG).runTask().join();
        }
    }

    @Test
    void testJarClassifierDoesNotExposeReactorMainOrUnpackagedTestRoots() throws Exception {
        MavenProject app = project("app");
        ArtifactResolver resolver = resolver(app);
        Artifact classified = artifact("app", jar("test artifact"), "test", "tests");
        assertInstanceOf(JarDependency.class, resolver.toDependency(classified));
    }

    private MavenProject project(String name) throws IOException {
        MavenProject project = new MavenProject();
        Path base = Files.createDirectories(directory.resolve(name));
        project.setFile(base.resolve("pom.xml").toFile());
        project.setGroupId("example");
        project.setArtifactId(name);
        project.setVersion("1");
        Build build = new Build();
        build.setDirectory(base.resolve("target").toString());
        build.setSourceDirectory(Files.createDirectories(base.resolve("src/main/java/example/" + name)).getParent().getParent().toString());
        build.setTestSourceDirectory(Files.createDirectories(base.resolve("src/test/java")).toString());
        project.setBuild(build);
        project.addCompileSourceRoot(build.getSourceDirectory());
        project.addTestCompileSourceRoot(build.getTestSourceDirectory());
        project.setDependencyArtifacts(Set.of());
        project.setArtifacts(Set.of());
        String type = name.equals("app") ? "MainInput" : "Library";
        Files.writeString(Path.of(build.getSourceDirectory(), "example", name, type + ".java"),
                "package example." + name + "; public class " + type + " {}");
        Files.writeString(Path.of(build.getTestSourceDirectory(), "TestInput.java"), "package example.app; public class TestInput {}");
        return project;
    }

    private Resource resource(MavenProject project, boolean test, String folder, String name, String contents) throws IOException {
        Path root = Files.createDirectories(project.getBasedir().toPath().resolve(folder));
        Files.writeString(root.resolve(name), contents);
        Resource resource = new Resource();
        resource.setDirectory(root.toString());
        resource.setFiltering(true);
        if (test) project.addTestResource(resource); else project.addResource(resource);
        project.getProperties().setProperty("project.build.sourceEncoding", "UTF-8");
        return resource;
    }

    private Path jar(String contents) throws IOException {
        Path jar = directory.resolve("external.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("jar.txt"));
            zip.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    private Artifact artifact(String name, Path file, String scope, String classifier) {
        Artifact artifact = new DefaultArtifact("example", name, "1", scope, "jar", classifier, new DefaultArtifactHandler("jar"));
        if (file != null) artifact.setFile(file.toFile());
        return artifact;
    }

    private ArtifactResolver resolver(MavenProject... projects) {
        MavenSession session = new MavenSession(null, new DefaultRepositorySystemSession(),
                new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());
        session.setAllProjects(List.of(projects));
        session.setCurrentProject(projects[0]);
        session.setProjectDependencyGraph((ProjectDependencyGraph) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProjectDependencyGraph.class}, (proxy, method, args) -> List.of(projects).subList(1, projects.length)));
        return new ArtifactResolver(projects[0], null, List.of(), null, session, null, Map.of(), LOG) {
            @Override
            public java.util.Collection<org.treblereel.j2cl.plugin.model.Dependency> getDependencies(Artifact artifact) {
                return List.of();
            }
        };
    }

    private BuildContext context(MavenProject project, ArtifactResolver resolver, String root, String checks, String absent)
            throws Exception {
        return context(project, resolver, root, checks, absent, false);
    }

    private BuildContext context(MavenProject project, ArtifactResolver resolver, String root, String checks,
                                 String absent, boolean generate) throws Exception {
        Path processors = directory.resolve("processor.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(processors))) {
            for (Class<?> type : List.of(ResourceProcessor.class, TurbineResourcesTest.class)) {
                String name = type.getName().replace('.', '/') + ".class";
                zip.putNextEntry(new ZipEntry(name));
                try (var input = type.getClassLoader().getResourceAsStream(name)) {
                    zip.write(java.util.Objects.requireNonNull(input).readAllBytes());
                }
                zip.closeEntry();
            }
        }
        BuildConfig config = new BuildConfig(List.of(), List.of(), null, "app.js", directory.toString(),
                "ADVANCED_OPTIMIZATIONS", Map.of(), false, null, false, "ECMASCRIPT_2017", true, "BROWSER",
                Map.of("resource.root", root, "resource.checks", checks, "resource.absent", absent, "resource.generate", Boolean.toString(generate)),
                List.of(new AptPath(processors.toFile(), List.of(ResourceProcessor.class.getName()))));
        return new BuildContext(project, config, resolver, null);
    }

    public static class ResourceProcessor extends AbstractProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() { return Set.of("*"); }
        @Override
        public Set<String> getSupportedOptions() { return Set.of("resource.root", "resource.checks", "resource.absent", "resource.generate"); }
        @Override
        public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (round.getRootElements().stream().noneMatch(e -> e.toString().equals(processingEnv.getOptions().get("resource.root")))) return false;
            try {
                for (String check : processingEnv.getOptions().get("resource.checks").split("\\|")) {
                    String[] parts = check.split("=", 2);
                    var resource = processingEnv.getFiler().getResource(StandardLocation.ANNOTATION_PROCESSOR_PATH, "", parts[0]);
                    if (!parts[1].equals(resource.getCharContent(false).toString())) throw new AssertionError(check);
                }
                for (String absent : java.util.Objects.toString(processingEnv.getOptions().get("resource.absent"), "").split("\\|")) {
                    if (absent.isEmpty()) continue;
                    try {
                        processingEnv.getFiler().getResource(StandardLocation.ANNOTATION_PROCESSOR_PATH, "", absent);
                        throw new AssertionError("Resource must not be visible: " + absent);
                    } catch (java.io.FileNotFoundException expected) {
                        // Excluded and test-only resources must stay out of this processor invocation.
                    }
                }
                if (Boolean.parseBoolean(processingEnv.getOptions().get("resource.generate"))) {
                    try (var writer = processingEnv.getFiler().createSourceFile("example.GeneratedResourceProof").openWriter()) {
                        writer.write("package example; public class GeneratedResourceProof {}");
                    }
                    try (var writer = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT,
                            "example", "GeneratedResourceProof.native.js").openWriter()) {
                        writer.write("// generated native output");
                    }
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return false;
        }
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
