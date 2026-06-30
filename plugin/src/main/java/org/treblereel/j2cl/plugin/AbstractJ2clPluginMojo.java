package org.treblereel.j2cl.plugin;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.log.MavenBuildLog;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.xbt.TranslationsFileConfig;

public abstract class AbstractJ2clPluginMojo extends AbstractMojo {

    /**
     * Compile options.
     */
    @Parameter(property = "sayhello.name", defaultValue = "World")
    protected String name;

    @Parameter(defaultValue = "${project.artifactId}/${project.artifactId}.js", required = true)
    protected String initialScriptFilename;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
    protected String webappDirectory;

    /**
     * Describes how the output should be built - presently supports five modes, four of which are closure-compiler
     * "compilationLevel" argument options, and an additional special case for J2cl-base applications. The quoted
     * descriptions here explain how closure-compiler defines them.
     * <ul>
     *     <li>
     *         {@code ADVANCED_OPTIMIZATIONS} - "ADVANCED_OPTIMIZATIONS aggressively reduces code size by renaming
     *         function names and variables, removing code which is never called, etc." This is typically what is
     *         expected for production builds.
     *     </li>
     *     <li>
     *         {@code SIMPLE_OPTIMIZATIONS} - "SIMPLE_OPTIMIZATIONS performs transformations to the input JS that
     *         do not require any changes to JS that depend on the input JS." Generally not useful in this plugin -
     *         slower than BUNDLE, much bigger than ADVANCED_OPTIMIZATIONS
     *     </li>
     *     <li>
     *         {@code WHITESPACE_ONLY} - "WHITESPACE_ONLY removes comments and extra whitespace in the input JS."
     *         Generally not useful in this plugin - slower than BUNDLE, much bigger than ADVANCED_OPTIMIZATIONS
     *     </li>
     *     <li>
     *         {@code BUNDLE} - "Simply orders and concatenates files to the output." The GWT fork of closure also
     *         prepends define statements, and provides wiring for sourcemaps.
     *     </li>
     *     <li>
     *         {@code BUNDLE_JAR} - Not a "real" closure-compiler option. but instead invokes BUNDLE on each
     *         classpath entry and generates a single JS file which will load those bundled files in order. Enables
     *         the compiler to cache results for each dependency, rather than re-generate a single large JS file.
     *     </li>
     * </ul>
     */
    @Parameter(defaultValue = "ADVANCED_OPTIMIZATIONS", property = "compilationLevel")
    protected String compilationLevel;

    /**
     * Closure flag: "Override the value of a variable annotated {@code @define}. The format is
     * {@code &lt;name&gt;[=&lt;val&gt;]}, where {@code &lt;name&gt;} is the name of a {@code @define}
     * variable and {@code &lt;val&gt;} is a boolean, number, or a single-quoted string that contains
     * no single quotes. If {@code [=&lt;val&gt;]} is omitted, the variable is marked true"
     * <p></p>
     * In this plugin the format is to provided tags for each define key, where the text contents will represent the
     * value.
     * <p></p>
     * In the context of J2CL and Java, this can be used to define values for system properties.
     */
    @Parameter
    protected Map<String, Object> defines = new TreeMap<>();

    /**
     * Closure flag: "Rewrite ES6 library calls to use polyfills provided by the compiler's runtime."
     * Unlike in closure-compiler, defaults to false.
     */
    @Parameter(defaultValue = "false")
    protected boolean rewritePolyfills;

    /**
     * Closure flag: "Source of translated messages. Currently only supports XTB."
     */
    @Parameter
    protected TranslationsFileConfig translationsFile;

    /**
     * True to enable sourcemaps to be built into the project output.
     */
    @Parameter(defaultValue = "false")
    protected boolean enableSourcemaps;

    /**
     * Closure flag: configures the ECMAScript output level.
     */
    @Parameter(defaultValue = "ECMASCRIPT_2017", property = "languageOut")
    protected String languageOut;

    /**
     * Whether to keep J2CL assertion checks in the compiled output.
     * When false (default for compile), assertions are stripped for smaller output.
     */
    @Parameter(defaultValue = "false")
    protected boolean checkAssertions;

    /**
     * Closure Compiler environment. Determines which builtin externs to load.
     */
    @Parameter(defaultValue = "BROWSER")
    protected String env;

    /**
     * Arguments to pass to annotation processors, in the form of key-value pairs.
     */
    @Parameter
    protected Map<String, String> annotationProcessorsArgs = new TreeMap<>();

    /**
     * Maven-specific parameters.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    protected List<RemoteRepository> remoteRepos;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    protected MojoExecution mojoExecution;

    /**
     * Dependencies coordinates.
     */

    @Parameter(defaultValue = "org.kie.j2cl.tools:jre:" + Versions.J2CL_VERSION, required = true)
    protected String jreJar;

    @Parameter(defaultValue = "org.kie.j2cl.tools:javac-bootstrap-classpath:" + Versions.J2CL_VERSION, required = true, alias = "javacBootstrapClasspathJar")
    protected String bootstrapClasspath;

    @Parameter(defaultValue = "org.kie.j2cl.tools:jre:zip:jszip:" + Versions.J2CL_VERSION, required = true)
    protected String jreJsZip;

    @Parameter(defaultValue = "org.kie.j2cl.tools:bootstrap:zip:jszip:" + Versions.J2CL_VERSION, required = true)
    protected String bootstrapJsZip;

    @Parameter(defaultValue = "org.jspecify:jspecify:1.0.0", required = true)
    protected String jspecify;

    @Parameter(defaultValue = "org.kie.j2cl.tools:gwt-internal-annotations:" + Versions.J2CL_VERSION, required = true)
    protected String internalAnnotationsJar;

    @Parameter(defaultValue = "com.google.jsinterop:jsinterop-annotations:2.1.0", required = true)
    protected String jsinteropAnnotationsJar;

    @Parameter(defaultValue = "org.kie.j2cl.tools.jsinterop:jsinterop-base:1.1.1", required = true)
    protected String jsinteropBaseJar;

    @Parameter(defaultValue = "org.kie.j2cl.tools:closure-test:zip:jszip:" + Versions.J2CL_VERSION, required = true)
    protected String testJsZip;

    @Parameter(defaultValue = "org.kie.j2cl.tools:junit-runtime:" + Versions.J2CL_VERSION, required = true)
    protected String runtime;

    @Parameter(defaultValue = "org.kie.j2cl.tools:junit-runtime:zip:jszip:" + Versions.J2CL_VERSION, required = true)
    protected String runtimeJsZip;

    @Parameter(defaultValue = "org.kie.j2cl.tools:junit-annotations:" + Versions.J2CL_VERSION, required = true)
    protected String junitAnnotations;

    @Parameter(defaultValue = "org.kie.j2cl.tools:junit-emul:" + Versions.J2CL_VERSION, required = true)
    protected String junitEmul;

    @Parameter(defaultValue = "org.kie.j2cl.tools:gwttestcase-emul:" + Versions.J2CL_VERSION, required = true)
    protected String gwttestcaseEmul;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Component
    protected RepositorySystem repoSystem;

    @Component
    protected ProjectBuilder projectBuilder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        BuildLog buildLog = new MavenBuildLog(this);

        Map<String, org.apache.maven.artifact.Artifact> defaultDependencyReplacement = new HashMap<>();
        defaultDependencyReplacement.put("com.google.jsinterop:base", new org.apache.maven.artifact.DefaultArtifact(
                "org.kie.j2cl.tools.jsinterop",
                "jsinterop-base",
                "1.1.1",
                "compile",
                "jar",
                null,
                new org.apache.maven.artifact.handler.DefaultArtifactHandler()
        ));
        defaultDependencyReplacement.put("org.gwtproject:gwt-user", null);
        defaultDependencyReplacement.put("org.gwtproject:gwt-dev", null);
        defaultDependencyReplacement.put("org.gwtproject:gwt-servlet", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-user", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-dev", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-servlet", null);

        ArtifactResolver artifactResolver = new ArtifactResolver(
                project,
                repoSystem,
                remoteRepos,
                repoSession,
                session,
                projectBuilder,
                defaultDependencyReplacement,
                buildLog
        );

        List<File> extraClasspath = Arrays.asList(
                getFileWithMavenCoords(jreJar),
                getFileWithMavenCoords(jsinteropAnnotationsJar),
                getFileWithMavenCoords(internalAnnotationsJar),
                getFileWithMavenCoords(jsinteropBaseJar),
                getFileWithMavenCoords(jspecify)
        );

        List<Artifact> extraJsZips = Arrays.asList(
                getMavenArtifactWithCoords(bootstrapJsZip),
                getMavenArtifactWithCoords(jreJsZip)
        );

        File bootstrapClasspath = getFileWithMavenCoords(this.bootstrapClasspath);

        BuildConfig buildConfig = new BuildConfig(
                extraClasspath,
                extraJsZips,
                bootstrapClasspath,
                initialScriptFilename,
                webappDirectory,
                compilationLevel,
                defines,
                rewritePolyfills,
                translationsFile,
                enableSourcemaps,
                languageOut,
                checkAssertions,
                env,
                annotationProcessorsArgs
        );

        BuildContext buildContext = new BuildContext(
                project,
                buildConfig,
                artifactResolver,
                new PluginParameterExpressionEvaluator(session, mojoExecution)
        );

        ReactorDependency project = new ReactorDependency(this.project, artifactResolver);

        process(project, buildContext, buildLog);
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

    protected abstract void process(ReactorDependency project, BuildContext buildContext, BuildLog buildLog);
}
