package org.treblereel.j2cl.plugin.context;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.tools.APTProcessors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationProcessorArtifactTest {

    @TempDir
    Path directory;

    private final ArtifactTypeRegistry types = type -> switch (type) {
        case "jar" -> new DefaultArtifactType("jar", "jar", "", "java");
        case "test-jar" -> new DefaultArtifactType("test-jar", "jar", "tests", "java");
        default -> null;
    };

    @Test
    void defaultsToUnclassifiedJar() {
        assertArtifact(null, null, "jar", "");
        assertArtifact("", "", "jar", "");
    }

    @Test
    void preservesExplicitClassifier() {
        assertArtifact(null, "processor", "jar", "processor");
        assertArtifact("jar", "jdk25", "jar", "jdk25");
    }

    @Test
    void resolvesMavenTypeToExtensionAndDefaultClassifier() {
        assertArtifact("test-jar", null, "jar", "tests");
    }

    @Test
    void explicitClassifierOverridesTypeDefault() {
        assertArtifact("test-jar", "processor", "jar", "processor");
    }

    @Test
    void unknownTypeUsesItsNameAsExtension() {
        assertArtifact("zip", "processor", "zip", "processor");
    }

    @Test
    void configuredClassifierSelectsTheProcessorJar() throws Exception {
        Path jar = directory.resolve("apt-1.2-processor.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("META-INF/services/javax.annotation.processing.Processor"));
            zip.write("example.ClassifiedProcessor\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MavenProject project = new MavenProject();
        project.setBuild(new Build());
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-compiler-plugin");
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom paths = new Xpp3Dom("annotationProcessorPaths");
        Xpp3Dom path = new Xpp3Dom("path");
        for (var field : Map.of("groupId", "example", "artifactId", "apt", "version", "1.2",
                "classifier", "processor", "type", "test-jar").entrySet()) {
            Xpp3Dom child = new Xpp3Dom(field.getKey());
            child.setValue(field.getValue());
            path.addChild(child);
        }
        paths.addChild(path);
        config.addChild(paths);
        plugin.setConfiguration(config);
        project.getBuild().addPlugin(plugin);

        var repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setArtifactTypeRegistry(types);
        MavenSession session = new MavenSession(null, repositorySession,
                new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());
        session.setAllProjects(List.of(project));
        RepositorySystem repository = (RepositorySystem) Proxy.newProxyInstance(
                RepositorySystem.class.getClassLoader(), new Class<?>[]{RepositorySystem.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("resolveArtifact")) throw new UnsupportedOperationException();
                    ArtifactRequest request = (ArtifactRequest) args[1];
                    assertEquals("example:apt:jar:processor:1.2", request.getArtifact().toString());
                    return new ArtifactResult(request).setArtifact(request.getArtifact().setFile(jar.toFile()));
                });
        ArtifactResolver resolver = new ArtifactResolver(project, repository, List.of(), repositorySession,
                session, null, Map.of(), null);
        var processors = APTProcessors.getAPTProcessorPaths(project, resolver, null);
        assertEquals(1, processors.size());
        assertEquals(jar.toFile(), processors.getFirst().annotationProcessorFile());
        assertEquals(List.of("example.ClassifiedProcessor"), processors.getFirst().annotationProcessorName());
    }

    private void assertArtifact(String type, String classifier, String extension, String expectedClassifier) {
        Artifact artifact = ArtifactResolver.typedArtifact("example", "apt", "1.2", type, classifier, types);
        assertEquals("example", artifact.getGroupId());
        assertEquals("apt", artifact.getArtifactId());
        assertEquals("1.2", artifact.getVersion());
        assertEquals(extension, artifact.getExtension());
        assertEquals(expectedClassifier, artifact.getClassifier());
    }
}
