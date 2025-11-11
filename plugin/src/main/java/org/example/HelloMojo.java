package org.example;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
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

    @Parameter(defaultValue = "org.jspecify:jspecify:1.0.0", required = true)
    protected String jspecify;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("👋 Hello, " + name + "!");
        ArtifactResolver artifactResolver = new ArtifactResolver(repoSystem, remoteRepos, repoSession, session, getLog());

        List<File> extraClasspath = Arrays.asList(
                getFileWithMavenCoords(jspecify)
        );


        BuildConfig buildConfig = new BuildConfig(extraClasspath);

        BuildContext buildContext = new BuildContext(project, buildConfig, artifactResolver);


        try {
            ArtifactResult artifactResult = repoSystem.resolveArtifact(repoSession, new ArtifactRequest()
                    .setArtifact(new DefaultArtifact("org.apache.commons:commons-lang3:3.12.0"))
                    .setRepositories(remoteRepos)
            );


        } catch (ArtifactResolutionException e) {
            throw new RuntimeException(e);
        }

        project.getDependencies().stream().forEach(dependency -> {
            getLog().info("Dependency: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
            try {
                printTransitiveDeps(dependency);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });


/*    project.getArtifacts().forEach(a -> {
      getLog().info(String.format(
              "%s:%s:%s:%s:%s  [scope=%s] -> %s",
              a.getGroupId(),
              a.getArtifactId(),
              a.getType(),
              a.getVersion(),
              a.getClassifier() != null ? a.getClassifier() : "",
              a.getScope(),
              a.getFile() != null ? a.getFile().getAbsolutePath() : "(not resolved)"
      ));
    });


    Stack<Dependency> queue = new Stack<>();
    Dependency root = new Dependency(project.getGroupId(), project.getArtifactId(), project.getVersion(), project.getPackaging(), "", "");
    queue.push(root);
    while (!queue.isEmpty()) {
      Dependency dep = queue.pop();

    }*/

        Project project = new Project(this.project, artifactResolver);


        try {
            new ByteCodeTask(project, buildContext).runTask().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //resolveSources("org.apache.commons", "commons-lang3", "3.12.0");
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

    private void resolveSources(String g, String a, String v) {
        Artifact sources = new DefaultArtifact(g, a, "sources", "jar", v);

        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(sources);
        req.setRepositories(remoteRepos);

        try {
            ArtifactResult res = repoSystem.resolveArtifact(repoSession, req);
            res.getArtifact().getFile();

            File file = res.getArtifact().getFile();
            getLog().info("Sources resolved: " + (file != null ? file.getAbsolutePath() : "(no file)"));
        } catch (Exception e) {
            getLog().warn("No sources found for " + g + ":" + a + ":" + v + " (" + e.getClass().getSimpleName() + ")");
        }
    }
}
