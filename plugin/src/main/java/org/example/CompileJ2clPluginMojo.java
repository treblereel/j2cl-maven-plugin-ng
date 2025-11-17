package org.example;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Project;
import org.example.task.FinalTask;


@Mojo(
        name = "compile",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CompileJ2clPluginMojo extends AbstractJ2clPluginMojo {

    protected void process(Project project, BuildContext buildContext, BuildLog buildLog) {
        try {
            new FinalTask(project, buildContext, buildLog).runTask().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
