package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.turbine.diag.TurbineError;
import com.google.turbine.main.Main;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.TurbineRunner;

public class TurbineCodeTask extends ByteCodeTask {

    public TurbineCodeTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    protected void doCompile(List<String> sources, Set<String> moduleDependencies,
                             List<String> annotationProcessorJars,
                             List<String> annotationProcessorNames,
                             List<String> javacOpts, File output) {
        if (buildContext.isWatchMode() && !annotationProcessorNames.isEmpty() && !sources.isEmpty()) {
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

        List<String> arguments = new ArrayList<>(List.of("--output", output.toString(),
                "--gensrc_output", outputPath().toString(), "--resource_output", outputPath().toString(), "--sources"));
        arguments.addAll(sources);
        arguments.add("--processorpath");
        arguments.addAll(processorPath);
        arguments.add("--processors");
        arguments.addAll(annotationProcessorNames);
        arguments.add("--classpath");
        arguments.addAll(moduleDependencies);
        arguments.add("--javacopts");
        arguments.addAll(javacOpts);
        arguments.addAll(List.of("-source", "21", "-target", "21", "--release", "21", "--"));
        try {
            if (File.separatorChar == '\\') {
                TurbineRunner.compile(arguments);
            } else {
                Main.Result result = Main.compile(arguments.toArray(String[]::new));
                result.processorStatistics().processingTime().forEach((k, d) -> {
                    logger.debug("Turbine " + k + " took " + d.toMillis() + "ms");
                });
            }
        } catch (TurbineError e) {
            throw new RuntimeException(
                    "Turbine compilation failed at dependency " + dependency.key(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
