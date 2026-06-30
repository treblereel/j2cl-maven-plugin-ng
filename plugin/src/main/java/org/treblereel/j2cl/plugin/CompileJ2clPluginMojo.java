package org.treblereel.j2cl.plugin;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.task.BundleJarTask;
import org.treblereel.j2cl.plugin.task.FinalTask;


@Mojo(
        name = "compile",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CompileJ2clPluginMojo extends AbstractJ2clPluginMojo {

    protected void process(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        try {
            if ("BUNDLE_JAR".equalsIgnoreCase(buildContext.getConfig().compilationLevel())) {
                new BundleJarTask(project, buildContext, buildLog).runTask().join();
            } else {
                new FinalTask(project, buildContext, buildLog).runTask().join();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
