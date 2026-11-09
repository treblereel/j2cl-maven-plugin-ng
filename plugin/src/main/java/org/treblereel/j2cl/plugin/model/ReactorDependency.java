package org.treblereel.j2cl.plugin.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.tools.Hashing;
import org.treblereel.j2cl.plugin.tools.MavenResourceInputs;

public class ReactorDependency implements Dependency {

    private final MavenProject project;
    private final ArtifactResolver artifactResolver;
    private final List<Path> additionalSourcePaths = new ArrayList<>();
    private final List<Path> additionalSourceDirectories = new ArrayList<>();

    public ReactorDependency(MavenProject project, ArtifactResolver artifactResolver) {
        this.project = project;
        this.artifactResolver = artifactResolver;
    }

    public void addAdditionalSourcePath(Path path) {
        additionalSourcePaths.add(path);
    }

    public void addAdditionalSourceDirectory(Path path) {
        additionalSourceDirectories.add(path);
    }

    @Override
    public Collection<Dependency> getDependencies() {
        if (project.getDependencyArtifacts() == null) {
            return Set.of();
        }
        return project.getDependencyArtifacts()
                .stream()
                .filter(artifact -> !artifact.getScope().equals("provided"))
                .filter(artifact -> !artifact.getScope().equals("test"))
                .map(artifactResolver::toDependency)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public void setDependency(Artifact artifact) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSourceMapped() {
        return true;
    }

    @Override
    public String key() {
        return String.format("%s-%s-%s", project.getGroupId(), project.getArtifactId(), project.getVersion());
    }

    @Override
    public String groupId() {
        return project.getGroupId();
    }

    @Override
    public String artifactId() {
        return project.getArtifactId();
    }

    @Override
    public String version() {
        return project.getVersion();
    }

    public MavenProject getMavenProject() {
        return project;
    }

    protected ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    public List<Path> getSourcePaths() {
        List<Path> result = new ArrayList<>();
        Path sources = Paths.get(project.getBuild().getSourceDirectory());
        if(Files.exists(sources)) {
            result.add(sources);
        }

        for(Resource resource: project.getBuild().getResources()) {
            Path resourcePath = Paths.get(resource.getDirectory());
            if(Files.exists(resourcePath)) {
                result.add(resourcePath);
            }
        }

        if (artifactResolver != null && artifactResolver.isIncludeCompileSourceRoots()) {
            for (String root : project.getCompileSourceRoots()) {
                Path path = Paths.get(root);
                if (Files.exists(path) && !result.contains(path)) {
                    result.add(path);
                }
            }
        }

        result.addAll(additionalSourceDirectories);

        return result;
    }

    public List<Artifact> getResourceArtifacts() {
        return project.getArtifacts().stream()
                .filter(a -> this instanceof TestReactorDependency || !"test".equals(a.getScope()))
                .toList();
    }

    public List<MavenProject> getResourceProjects() {
        List<MavenProject> projects = new ArrayList<>();
        projects.add(project);
        if (artifactResolver != null) {
            getResourceArtifacts().stream().filter(this::isReactorResource)
                    .map(artifactResolver::getMavenProject).filter(p -> !projects.contains(p)).forEach(projects::add);
        }
        return projects;
    }

    private boolean isReactorResource(Artifact artifact) {
        return artifactResolver != null && artifactResolver.isInReactor(artifact)
                && !artifact.hasClassifier() && "jar".equals(artifact.getType());
    }

    public List<Path> getWatchSourcePaths() {
        List<Path> result = new ArrayList<>(getSourcePaths());
        result.addAll(additionalSourcePaths);
        if (artifactResolver != null && artifactResolver.isIncludeCompileSourceRoots()) {
            project.getCompileSourceRoots().stream().map(Paths::get).forEach(result::add);
        }
        for (MavenProject current : getResourceProjects()) {
            current.getResources().stream().map(r -> Path.of(r.getDirectory())).forEach(result::add);
            if (current == project && this instanceof TestReactorDependency) {
                current.getTestResources().stream().map(r -> Path.of(r.getDirectory())).forEach(result::add);
            }
            result.addAll(MavenResourceInputs.filterPaths(current, current == project && this instanceof TestReactorDependency,
                    artifactResolver == null ? null : artifactResolver.getMavenSession()));
            for (MavenProject parent = current; parent != null; parent = parent.getParent()) {
                if (parent.getFile() != null) result.add(parent.getFile().toPath());
            }
        }
        getResourceArtifacts().stream().filter(a -> !isReactorResource(a) || "provided".equals(a.getScope())).map(Artifact::getFile)
                .filter(Objects::nonNull).map(java.io.File::toPath).forEach(result::add);
        return result.stream().distinct().toList();
    }

    public List<Path> getHashPaths() {
        List<Path> result = new ArrayList<>(getSourcePaths());
        result.addAll(additionalSourcePaths);
        if (project != null) {
            for (MavenProject current : getResourceProjects()) {
                for (MavenProject parent = current; parent != null; parent = parent.getParent()) {
                    if (parent.getFile() != null && parent.getFile().isFile()) result.add(parent.getFile().toPath());
                }
                current.getResources().stream().map(r -> Path.of(r.getDirectory()))
                        .filter(Files::exists).forEach(result::add);
                MavenResourceInputs.filterPaths(current, current == project && this instanceof TestReactorDependency,
                        artifactResolver == null ? null : artifactResolver.getMavenSession()).stream()
                        .filter(Files::exists).forEach(result::add);
            }
            getResourceArtifacts().stream().filter(a -> !isReactorResource(a) || "provided".equals(a.getScope()))
                    .map(Artifact::getFile).filter(Objects::nonNull).map(java.io.File::toPath)
                    .filter(Files::exists).forEach(result::add);
        }
        return result.stream().distinct().toList();
    }

    public String getHash() {
        String inputs = Hashing.hash(getHashPaths());
        if (project == null) return inputs;
        String resources = getResourceProjects().stream()
                .map(current -> MavenResourceInputs.fingerprint(current, current == project && this instanceof TestReactorDependency,
                        artifactResolver == null ? null : artifactResolver.getMavenSession()))
                .collect(Collectors.joining());
        return inputs + com.google.common.hash.Hashing.sha256().hashUnencodedChars(resources).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ReactorDependency that)) return false;
        return Objects.equals(project, that.project);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(project);
    }
}
