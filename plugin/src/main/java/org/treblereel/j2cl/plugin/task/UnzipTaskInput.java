package org.treblereel.j2cl.plugin.task;

import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.JarDependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.utils.FileUtils;

import java.io.IOException;

public class UnzipTaskInput extends TaskInput {

  public UnzipTaskInput(Dependency dep, BuildContext buildContext, BuildLog logger) {
    super(dep, buildContext, logger);
  }

  @Override
  public OutputTypes getOutputTypes() {
    return OutputTypes.UNZIPPED_DEPENDENCIES;
  }

  @Override
  public void process() {
    if (!dependency.isSourceMapped()) {
      FileUtils.extractZip(((JarDependency)dependency).bytecodeJar(), outputPath(), dependency);
    } else {
      ((ReactorDependency) dependency).getSourcePaths().forEach(resourcePath -> {
        try {
          FileUtils.copyDirectory(resourcePath, outputPath());
        } catch (IOException e) {
          throw new RuntimeException("Failed to copy resources for dependency " + dependency.key(), e);
        }
      });
    }
  }

}
