package org.example;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.example.config.BuildConfig;
import org.example.context.ArtifactResolver;
import org.example.context.BuildContext;
import org.example.model.Dependency;
import org.example.model.Project;
import org.example.task.ByteCodeTask;
import org.example.task.StripSourcesTask;
import org.example.task.TaskInput;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

@Mojo(
        name = "compile",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class HelloMojo extends AbstractMojo {

    @Parameter(property = "sayhello.name", defaultValue = "World")
    private String name;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private java.util.List<RemoteRepository> remoteRepos;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private MojoExecution mojoExecution;

    @Parameter(defaultValue = "org.kie.j2cl.tools:jre:v20250822-1", required = true)
    protected String jreJar;

    @Parameter(defaultValue = "org.jspecify:jspecify:1.0.0", required = true)
    protected String jspecify;

    @Parameter(defaultValue = "org.kie.j2cl.tools:gwt-internal-annotations:v20250822-1", required = true)
    protected String internalAnnotationsJar;

    @Parameter(defaultValue = "com.google.jsinterop:jsinterop-annotations:2.1.0", required = true)
    protected String jsinteropAnnotationsJar;

    @Parameter(defaultValue = "org.kie.j2cl.tools.jsinterop:jsinterop-base:1.1.1", required = true)
    protected String jsinteropBaseJar;

    @Parameter(defaultValue = "org.kie.j2cl.tools:bootstrap:zip:jszip:v20250822-1", required = true)
    protected String bootstrapJsZip;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("👋 Hello, " + name + "!");
        ArtifactResolver artifactResolver = new ArtifactResolver(repoSystem, remoteRepos, repoSession, session, getLog());

        List<File> extraClasspath = Arrays.asList(
                getFileWithMavenCoords(jreJar),
                getFileWithMavenCoords(jsinteropAnnotationsJar),
                getFileWithMavenCoords(internalAnnotationsJar),
                getFileWithMavenCoords(jsinteropBaseJar),
                getFileWithMavenCoords(jspecify)
        );


        BuildConfig buildConfig = new BuildConfig(extraClasspath);

        BuildContext buildContext = new BuildContext(project, buildConfig, artifactResolver, new PluginParameterExpressionEvaluator(session, mojoExecution));


        project.getDependencies().stream().forEach(dependency -> {
            getLog().info("Dependency: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
            try {
                printTransitiveDeps(dependency);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });

        Project project = new Project(this.project, artifactResolver);


        try {
            new StripSourcesTask(project, buildContext).runTask().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void printTransitiveDeps(org.apache.maven.model.Dependency dep) throws Exception {
        String coords = String.format("%s:%s:%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
        var artifact = new DefaultArtifact(coords);

        var request = new CollectRequest();
        request.setRoot(new org.eclipse.aether.graph.Dependency(artifact, dep.getScope()));
        request.setRepositories(remoteRepos);

        DependencyNode root = repoSystem.collectDependencies(repoSession, request).getRoot();

        getLog().info("Dependencies for " + coords + ":");
        root.getChildren().forEach(child -> {
            var cdep = child.getDependency().getArtifact();
            getLog().info("  ↳ " + cdep.getGroupId() + ":" + cdep.getArtifactId() + ":" + cdep.getVersion());
        });
    }

    protected File getFileWithMavenCoords(String coords) throws MojoExecutionException {
        ArtifactRequest request = new ArtifactRequest()
                .setRepositories(remoteRepos)
                .setArtifact(new DefaultArtifact(coords));

        try {
            return repoSystem.resolveArtifact(repoSession, request).getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to find artifact " + coords, e);
        }
    }
}
