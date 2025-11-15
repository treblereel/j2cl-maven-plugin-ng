package org.example;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.example.config.BuildConfig;
import org.example.context.ArtifactResolver;
import org.example.context.BuildContext;
import org.example.model.Dependency;
import org.example.model.Project;
import org.example.task.FinalTask;
import org.example.task.J2CLTask;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

  @Parameter(defaultValue = "org.kie.j2cl.tools:javac-bootstrap-classpath:v20250822-1", required = true, alias = "javacBootstrapClasspathJar")
  protected String bootstrapClasspath;

  @Parameter(defaultValue = "org.kie.j2cl.tools:jre:zip:jszip:v20250822-1", required = true)
  protected String jreJsZip;

  @Parameter(defaultValue = "org.kie.j2cl.tools:bootstrap:zip:jszip:v20250822-1", required = true)
  protected String bootstrapJsZip;

  @Parameter(defaultValue = "org.jspecify:jspecify:1.0.0", required = true)
  protected String jspecify;

  @Parameter(defaultValue = "org.kie.j2cl.tools:gwt-internal-annotations:v20250822-1", required = true)
  protected String internalAnnotationsJar;

  @Parameter(defaultValue = "com.google.jsinterop:jsinterop-annotations:2.1.0", required = true)
  protected String jsinteropAnnotationsJar;

  @Parameter(defaultValue = "org.kie.j2cl.tools.jsinterop:jsinterop-base:1.1.1", required = true)
  protected String jsinteropBaseJar;

  @Parameter(defaultValue = "org.kie.j2cl.tools:closure-test:zip:jszip:v20250822-1", required = true)
  protected String testJsZip;

  @Parameter(defaultValue = "org.kie.j2cl.tools:junit-runtime:v20250822-1", required = true)
  protected String runtime;

  @Parameter(defaultValue = "org.kie.j2cl.tools:junit-runtime:zip:jszip:v20250822-1", required = true)
  protected String runtimeJsZip;

  @Override
  public void execute() throws MojoExecutionException {


    Map<String, String> defaultDependencyReplacement = new HashMap<>();
    defaultDependencyReplacement.put("com.google.jsinterop:base", "org.kie.j2cl.tools.jsinterop:jsinterop-base:1.1.1");
    defaultDependencyReplacement.put("org.gwtproject:gwt-user", null);
    defaultDependencyReplacement.put("org.gwtproject:gwt-dev", null);
    defaultDependencyReplacement.put("org.gwtproject:gwt-servlet", null);
    defaultDependencyReplacement.put("com.google.gwt:gwt-user", null);
    defaultDependencyReplacement.put("com.google.gwt:gwt-dev", null);
    defaultDependencyReplacement.put("com.google.gwt:gwt-servlet", null);


    getLog().info("👋 Hello, " + name + "!");
    ArtifactResolver artifactResolver = new ArtifactResolver(repoSystem, remoteRepos, repoSession, session, defaultDependencyReplacement, getLog());

    List<File> extraClasspath = Arrays.asList(
            getFileWithMavenCoords(jreJar),
            getFileWithMavenCoords(jsinteropAnnotationsJar),
            getFileWithMavenCoords(internalAnnotationsJar),
            getFileWithMavenCoords(jsinteropBaseJar),
            getFileWithMavenCoords(jspecify)
    );

    List<org.eclipse.aether.graph.Dependency> extraJsZips = Arrays.asList(
            getAetherDependencyWithCoords(jreJsZip),
            getAetherDependencyWithCoords(bootstrapJsZip)
    );

    File bootstrapClasspath = getFileWithMavenCoords(this.bootstrapClasspath);

    BuildConfig buildConfig = new BuildConfig(extraClasspath, bootstrapClasspath, getLog());
    BuildContext buildContext = new BuildContext(project, buildConfig, artifactResolver, new PluginParameterExpressionEvaluator(session, mojoExecution));

    List<Dependency> dependencies = Stream.concat(artifactResolver.getDependencies(project.getGroupId(), project.getArtifactId(), project.getVersion(), "compile").stream(),
            extraJsZips.stream().map(artifact -> new Dependency(artifact, artifactResolver))
    ).toList();


    Project project = new Project(this.project, artifactResolver, dependencies);

    for (Dependency dependency : project.getDependencies()) {
      System.out.println("Dependency: " + dependency.key() + " jszip=" + dependency.isJsZip());
    }

    try {
      new FinalTask(project, buildContext).runTask().join();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  protected Artifact getMavenArtifactWithCoords(String coords) throws MojoExecutionException {
    ArtifactRequest request = new ArtifactRequest()
            .setRepositories(remoteRepos)
            .setArtifact(new DefaultArtifact(coords));

    try {
      ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
      return RepositoryUtils.toArtifact(result.getArtifact());
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException("Failed to find artifact " + coords, e);
    }
  }

  protected org.eclipse.aether.graph.Dependency getAetherDependencyWithCoords(String coords) throws MojoExecutionException {
    ArtifactRequest request = new ArtifactRequest()
            .setRepositories(remoteRepos)
            .setArtifact(new DefaultArtifact(coords));

    try {
      ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
      return new org.eclipse.aether.graph.Dependency(result.getArtifact(), "compile");
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException("Failed to find artifact " + coords, e);
    }
  }
}
