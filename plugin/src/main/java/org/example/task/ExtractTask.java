package org.example.task;

import org.apache.maven.artifact.Artifact;
import org.example.context.BuildContext;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class ExtractTask implements Task {


  private final Artifact artifact;
  private final BuildContext buildContext;

  public ExtractTask(BuildContext buildContext, Artifact artifact) {
    this.buildContext = buildContext;
    this.artifact = artifact;
  }

  @Override
  public File get() {
    return null;
  }
}
