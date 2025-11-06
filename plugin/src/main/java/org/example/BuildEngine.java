package org.example;

import org.apache.maven.project.MavenProject;
import org.example.context.BuildContext;
import org.example.task.ExtractTask;
import org.example.task.Task;

import java.io.File;

public class BuildEngine {


  private final BuildContext buildContext;

  public BuildEngine(BuildContext buildContext) {
    this.buildContext = buildContext;
  }

  public void build(MavenProject project) {
    project.getArtifacts().forEach(artifact -> {
      Task compileTask = new ExtractTask(buildContext, artifact);
      File output = compileTask.get();
    });


  }


}
