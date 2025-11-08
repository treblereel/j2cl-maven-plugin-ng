package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.turbine.diag.TurbineError;
import com.google.turbine.main.Main;
import com.google.turbine.options.LanguageVersion;
import com.google.turbine.options.TurbineOptions;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;

public class TurbineCodeTask extends ByteCodeTask {

    public TurbineCodeTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    protected void doCompile(List<String> sources, Set<String> moduleDependencies,
                             List<String> annotationProcessorJars,
                             List<String> annotationProcessorNames,
                             List<String> javacOpts, File output) {
        if (buildContext.isWatchMode() && !annotationProcessorNames.isEmpty()) {
            super.doCompile(sources, moduleDependencies, annotationProcessorJars,
                    annotationProcessorNames, javacOpts, output);
        } else {
            compileWithTurbine(sources, moduleDependencies, annotationProcessorJars,
                    annotationProcessorNames, javacOpts, output);
        }
    }

    private void compileWithTurbine(List<String> sources, Set<String> moduleDependencies,
                                    List<String> annotationProcessorJars,
                                    List<String> annotationProcessorNames,
                                    List<String> javacOpts, File output) {
        List<String> processorPath = new ArrayList<>(annotationProcessorJars);
        processorPath.addAll(moduleDependencies);

        try {
            Main.Result result = Main.compile(
                    TurbineOptions.builder()
                            .setProcessorPath(ImmutableList.copyOf(processorPath))
                            .setProcessors(ImmutableList.copyOf(annotationProcessorNames))
                            .setSources(ImmutableList.copyOf(sources))
                            .setOutput(output.toString())
                            .setGensrcOutput(outputPath().toFile().toString())
                            .setResourceOutput(outputPath().toFile().toString())
                            .setClassPath(ImmutableList.copyOf(moduleDependencies))
                            .addAllJavacOpts(javacOpts)
                            .setLanguageVersion(LanguageVersion.fromJavacopts(
                                    ImmutableList.of("-source", "21", "-target", "21", "--release", "21")))
                            .build());
            result.processorStatistics().processingTime().forEach((k, d) -> {
                logger.debug("Turbine " + k + " took " + d.toMillis() + "ms");
            });
        } catch (TurbineError e) {
            throw new RuntimeException(
                    "Turbine compilation failed at dependency " + dependency.key(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
