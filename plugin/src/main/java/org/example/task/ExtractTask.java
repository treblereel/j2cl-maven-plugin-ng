package org.example.task;

import org.apache.maven.artifact.Artifact;
import org.example.context.BuildContext;

import java.io.File;

public class ExtractTask implements Task {


  private final Artifact artifact;
  private final BuildContext buildContext;

  public ExtractTask(BuildContext buildContext, Artifact artifact) {
    this.buildContext = buildContext;
    this.artifact = artifact;
  }

  @Override
  public File get() {
    System.out.println("Starting extraction of artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());
    try {
      Thread.sleep(1000); // Simulate time-consuming extraction
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    System.out.println("Completed extraction of artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());

    return null;
  }
}
