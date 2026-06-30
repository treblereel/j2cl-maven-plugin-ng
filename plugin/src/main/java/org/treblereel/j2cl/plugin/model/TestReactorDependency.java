package org.treblereel.j2cl.plugin.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class TestReactorDependency extends ReactorDependency {

    private final ReactorDependency mainDependency;

    public TestReactorDependency(MavenProject project, ArtifactResolver artifactResolver, ReactorDependency mainDependency) {
        super(project, artifactResolver);
        this.mainDependency = mainDependency;
    }

    @Override
    public List<Path> getSourcePaths() {
        MavenProject project = getMavenProject();
        List<Path> result = new ArrayList<>();

        for (String testRoot : project.getTestCompileSourceRoots()) {
            if (testRoot.contains("generated-test-sources") || testRoot.contains("generated-sources")) {
                continue;
            }
            Path path = Paths.get(testRoot);
            if (Files.exists(path)) {
                result.add(path);
            }
        }

        for (Resource resource : project.getTestResources()) {
            Path path = Paths.get(resource.getDirectory());
            if (Files.exists(path)) {
                result.add(path);
            }
        }

        return result;
    }

    @Override
    public Collection<Dependency> getDependencies() {
        MavenProject project = getMavenProject();
        Collection<Dependency> deps = new ArrayList<>();
        deps.add(mainDependency);
        // Include test-scope dependencies (super.getDependencies() excludes them)
        deps.addAll(super.getDependencies());
        // Add test-scope deps that ReactorDependency filters out
        ArtifactResolver resolver = getArtifactResolver();
        project.getDependencyArtifacts()
                .stream()
                .filter(artifact -> "test".equals(artifact.getScope()))
                .map(artifact -> {
                    if (resolver.isInReactor(artifact)) {
                        return (Dependency) new ReactorDependency(resolver.getMavenProject(artifact), resolver);
                    }
                    return (Dependency) new JarDependency(artifact, resolver);
                })
                .forEach(deps::add);
        return deps;
    }

    @Override
    public String key() {
        return mainDependency.key() + "-test";
    }
}
