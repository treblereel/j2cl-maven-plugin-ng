package org.treblereel.j2cl.plugin.task;

import com.google.common.collect.ImmutableList;
import com.google.turbine.diag.TurbineError;
import com.google.turbine.main.Main;
import com.google.turbine.options.LanguageVersion;
import com.google.turbine.options.TurbineOptions;
import org.apache.maven.project.MavenProject;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.model.JarDependency;
import org.treblereel.j2cl.plugin.tools.AptPath;
import org.treblereel.j2cl.plugin.tools.J2CLModuleParser;
import org.treblereel.j2cl.plugin.tools.ServiceFileReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ByteCodeTask extends TaskInput {

    public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
    public static final PathMatcher JAVA_BYTECODE = withSuffix(".class");
    public static final PathMatcher NOT_BYTECODE = p -> !JAVA_BYTECODE.matches(p);
    public static final PathMatcher JAVA_MODULE_INFO = withSuffix("module-info.java");
    public static final PathMatcher TURBINE_OUTPUT = filename("output.jar");
    public static final PathMatcher JAVA_SOURCES_EXCEPT_MODULE_INFO = p -> JAVA_SOURCES.matches(p) && !JAVA_MODULE_INFO.matches(p);


    public static final PathMatcher APT_PROCESSOR = p ->
            p.equals(Paths.get("META-INF", "services", "javax.annotation.processing.Processor"));

    public ByteCodeTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    public OutputTypes getOutputTypes() {
        return OutputTypes.BYTECODE;
    }

    @Override
    public void process() {
        List<File> extraClasspath = buildContext.getConfig().getExtraClasspath();

        TaskOutput unzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        List<Path> superSourcePaths = J2CLModuleParser.getSuperSourcePaths(unzipped.paths());
        TaskOutput self = unzipped.filter(JAVA_SOURCES);

        Set<String> moduleDependencies = Stream.concat(extraClasspath.stream().map(File::toString),
                        input(dependency.getDependencies(), OutputTypes.BYTECODE)
                                .filter(TURBINE_OUTPUT)
                                .files()
                                .stream()
                                .map(f -> f.getAbsolutePath().toFile().toString()))
                .collect(Collectors.toSet());

        File output = new File(outputPath().toFile(), "output.jar");

        List<String> annotationProcessorJars = new ArrayList<>();
        List<String> annotationProcessorNames = new ArrayList<>();

        if (dependency.isSourceMapped()) {
            MavenProject project = ((ReactorDependency)dependency).getMavenProject();
            List<AptPath> aptPaths = buildContext.getAPTProcessorPaths(project);
            for (AptPath aptPath : aptPaths) {
                moduleDependencies.add(aptPath.annotationProcessorFile().getAbsolutePath());
                annotationProcessorJars.add(aptPath.annotationProcessorFile().getAbsolutePath());
                annotationProcessorNames.addAll(aptPath.annotationProcessorName());
            }

            for (org.apache.maven.artifact.Artifact artifact : project.getDependencyArtifacts()) {
                if (!"provided".equals(artifact.getScope()) || artifact.getFile() == null) continue;
                File file = artifact.getFile();
                List<String> processors = List.of();
                try {
                    if (file.isFile()) {
                        processors = ServiceFileReader.readProcessors(file.toPath());
                    } else if (file.isDirectory()) {
                        java.nio.file.Path serviceFile = file.toPath()
                                .resolve("META-INF/services/javax.annotation.processing.Processor");
                        if (Files.isRegularFile(serviceFile)) {
                            processors = Files.readAllLines(serviceFile).stream()
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                                    .toList();
                        }
                    }
                } catch (IOException e) {
                    // not an annotation processor
                }
                if (!processors.isEmpty()) {
                    String path = file.getAbsolutePath();
                    moduleDependencies.add(path);
                    annotationProcessorJars.add(path);
                    annotationProcessorNames.addAll(processors);

                    for (Dependency dep : buildContext.getArtifactResolver().getDependencies(artifact)) {
                        if (dep instanceof JarDependency jarDep && jarDep.bytecodeJar() != null) {
                            String depPath = jarDep.bytecodeJar().getAbsolutePath();
                            moduleDependencies.add(depPath);
                            annotationProcessorJars.add(depPath);
                        }
                    }
                }
            }

            if (!annotationProcessorNames.isEmpty()) {
                for (org.apache.maven.artifact.Artifact artifact : project.getDependencyArtifacts()) {
                    if ("provided".equals(artifact.getScope()) || "test".equals(artifact.getScope())) continue;
                    if (artifact.getFile() != null && artifact.getFile().isFile()) {
                        String depPath = artifact.getFile().getAbsolutePath();
                        moduleDependencies.add(depPath);
                        annotationProcessorJars.add(depPath);
                    }
                }
            }
        }

        List<AptPath> extraAptPaths = buildContext.getConfig().getExtraAnnotationProcessors();
        for (AptPath aptPath : extraAptPaths) {
            moduleDependencies.add(aptPath.annotationProcessorFile().getAbsolutePath());
            annotationProcessorJars.add(aptPath.annotationProcessorFile().getAbsolutePath());
            annotationProcessorNames.addAll(aptPath.annotationProcessorName());
        }

        List<String> javacOpts = new ArrayList<>();
        buildContext.getConfig().annotationProcessorsArgs().forEach((key, value) ->
                javacOpts.add("-A" + key + "=" + value));

        List<String> processorPath = new ArrayList<>(annotationProcessorJars);
        processorPath.addAll(moduleDependencies);

        try {

            Main.Result result = Main.compile(
                    TurbineOptions.builder()
                            //.setDirectJars()
                            //.setSourceJars(ImmutableList.of())
                            .setProcessorPath(ImmutableList.copyOf(processorPath))
                            .setProcessors(ImmutableList.copyOf(annotationProcessorNames))
                            .setSources(ImmutableList.copyOf(self.files().stream()
                                    .filter(f -> superSourcePaths.stream().noneMatch(f.getSourcePath()::startsWith))
                                    .map(f -> f.getAbsolutePath().toFile().toString()).toList()))
                            .setOutput(output.toString())
                            .setGensrcOutput(outputPath().toFile().toString())
                            .setResourceOutput(outputPath().toFile().toString())
                            .setClassPath(ImmutableList.copyOf(moduleDependencies))
                            .addAllJavacOpts(javacOpts)
                            .setLanguageVersion(LanguageVersion.fromJavacopts(
                                    ImmutableList.of("-source", "21", "-target", "21", "--release", "21")))
                            //TODO https://github.com/Vertispan/j2clmavenplugin/issues/181
                            //.setReducedClasspathMode(TurbineOptions.ReducedClasspathMode.JAVABUILDER_REDUCED)
                            .build());
            result.processorStatistics().processingTime().forEach((k, d) -> {
                logger.debug("Turbine " + k + " took " + d.toMillis() + "ms");
            });

        } catch (TurbineError e) {
            throw new RuntimeException("Turbine compilation failed at dependency " + dependency.key(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


}
