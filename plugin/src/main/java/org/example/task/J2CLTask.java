package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.io.File;
import java.nio.file.PathMatcher;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class J2CLTask extends TaskInput {

  public static final PathMatcher JAVA_SOURCES = withSuffix(".java");

  public static final PathMatcher JAVA_MODULE_INFO = withSuffix("module-info.java");

  public J2CLTask(Dependency dep, BuildContext buildContext) {
    super(dep, buildContext);
  }

  @Override
  public OutputTypes getOutputTypes() {
    return OutputTypes.TRANSPILED_JS;
  }

  @Override
  public void process() {
    TaskOutput selfStrippedSources = input(dep, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES);
    TaskOutput selfGeneratedSources = input(dep, OutputTypes.STRIPPED_BYTECODE).filter(JAVA_SOURCES);

    Set<String> moduleDependencies = Stream.concat(extraClasspath.stream().map(File::toString),
                    input(dep.getDependencies(), OutputTypes.BYTECODE)
                            .filter(TURBINE_OUTPUT)
                            .files()
                            .stream()
                            .map(f -> f.getAbsolutePath().toFile().toString()))
            .collect(Collectors.toSet());


  }
}
