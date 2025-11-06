package org.example;

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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.example.context.ArtifactResolver;
import org.example.context.BuildContext;

import java.io.File;

@Mojo(
        name = "compile",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
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

  @Component
  private RepositorySystem repoSystem;

  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("👋 Hello, " + name + "!");

    BuildContext buildContext = new BuildContext(new ArtifactResolver(repoSystem, remoteRepos, repoSession));




    project.getDependencies().stream().forEach(dependency -> {
      getLog().info("Dependency: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
    });

    project.getArtifacts().forEach(a -> {
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

    resolveSources("org.apache.commons", "commons-lang3", "3.12.0");
  }

  private void resolveSources(String g, String a, String v) {
    Artifact sources = new DefaultArtifact(g, a, "sources", "jar", v);

    ArtifactRequest req = new ArtifactRequest();
    req.setArtifact(sources);
    req.setRepositories(remoteRepos);

    try {
      ArtifactResult res = repoSystem.resolveArtifact(repoSession, req);
      File file = res.getArtifact().getFile();
      getLog().info("Sources resolved: " + (file != null ? file.getAbsolutePath() : "(no file)"));
    } catch (Exception e) {
      getLog().warn("No sources found for " + g + ":" + a + ":" + v + " (" + e.getClass().getSimpleName() + ")");
    }
  }
}
