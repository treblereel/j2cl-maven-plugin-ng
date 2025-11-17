package org.example.task;

import com.google.common.collect.ImmutableList;
import com.google.turbine.diag.TurbineError;
import com.google.turbine.main.Main;
import com.google.turbine.options.LanguageVersion;
import com.google.turbine.options.TurbineOptions;
import org.apache.maven.project.MavenProject;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.tools.AptPath;

import java.io.File;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

        TaskOutput self = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES).filter(JAVA_SOURCES);

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
            MavenProject project = dependency.asMavenProject();
            List<AptPath> aptPaths = buildContext.getAPTProcessorPaths(project);
            for (AptPath aptPath : aptPaths) {
                moduleDependencies.add(aptPath.annotationProcessorFile().getAbsolutePath());
                annotationProcessorJars.add(aptPath.annotationProcessorFile().getAbsolutePath());
                annotationProcessorNames.addAll(aptPath.annotationProcessorName());
            }
        }

        try {

            Main.Result result = Main.compile(
                    TurbineOptions.builder()
                            //.setDirectJars()
                            //.setSourceJars(ImmutableList.of())
                            .setProcessorPath(ImmutableList.copyOf(annotationProcessorJars))
                            .setProcessors(ImmutableList.copyOf(annotationProcessorNames))
                            .setSources(ImmutableList.copyOf(self.files().stream().map(f -> f.getAbsolutePath().toFile().toString()).toList()))
                            .setOutput(output.toString())
                            .setGensrcOutput(outputPath().toFile().toString())
                            .setResourceOutput(outputPath().toFile().toString())
                            .setClassPath(ImmutableList.copyOf(moduleDependencies))
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
