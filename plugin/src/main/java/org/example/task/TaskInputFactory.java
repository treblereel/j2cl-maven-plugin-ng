package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

public class TaskInputFactory {


  public static TaskInput create(Dependency dep, BuildContext buildContext, OutputTypes outputTypes) {
    return switch (outputTypes) {
      case BYTECODE -> new ByteCodeTask(dep, buildContext);
      case UNZIPPED_DEPENDENCIES -> new UnzipTaskInput(dep, buildContext);
      case STRIPPED_SOURCES -> new StripSourcesTask(dep, buildContext);
      case TRANSPILED_JS -> new J2CLTask(dep, buildContext);
      default -> throw new RuntimeException("Unsupported output type: " + outputTypes);
    };

  }
}
