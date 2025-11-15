package org.example.task;

import com.google.j2cl.common.SourceUtils;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.tools.J2cl;

import java.io.File;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class J2CLTask extends TaskInput {

  public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
  public static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");
  public static final PathMatcher TURBINE_OUTPUT = withSuffix("output.jar");

  public J2CLTask(Dependency dep, BuildContext buildContext) {
    super(dep, buildContext);
  }

  @Override
  public OutputTypes getOutputTypes() {
    return OutputTypes.TRANSPILED_JS;
  }

  @Override
  public void process() {
    if(dependency.isJsZip()) {
      // JsZip dependencies are already transpiled
      return;
    }

    //input(dependency.getDependencies(), OutputTypes.TRANSPILED_JS);

    TaskOutput self = input(dependency, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES);

    Stream<FileEntry> selfNative = Stream.concat(
            input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES).filter(NATIVE_JS_SOURCES).files().stream(),
            input(dependency, OutputTypes.BYTECODE).filter(NATIVE_JS_SOURCES).files().stream()
    );


    getAllDependencies().stream().peek(dep -> {
      System.out.println("Dependency: " + dep.key() + " " + dep.isJsZip());
    });

    Set<String> moduleDependencies = Stream.concat(
                    buildContext.getConfig().getExtraClasspath().stream().map(File::toString),
                    input(getAllDependencies(), OutputTypes.BYTECODE)
                            .filter(TURBINE_OUTPUT)
                            .files()
                            .stream()
                            .map(f -> f.getAbsolutePath().toFile().toString()))
            .collect(Collectors.toSet());

    File bootstrapClasspath = buildContext.getConfig().getBootstrapClasspath();

    J2cl j2cl = new J2cl(new ArrayList<>(moduleDependencies.stream().map(File::new).toList()),
            bootstrapClasspath,
            outputPath().toFile(),
            ((BuildLog) buildContext.getConfig())); //TODO

    List<SourceUtils.FileInfo> javaSources = self.filter(JAVA_SOURCES)
            .files()
            .stream()
            .filter(e -> JAVA_SOURCES.matches(e.getSourcePath()))
            .map(p -> SourceUtils.FileInfo.create(p.getAbsolutePath().toString(), p.getSourcePath().toString()))
            .toList();

    List<SourceUtils.FileInfo> nativeSources = selfNative
            .map(p -> SourceUtils.FileInfo.create(p.getAbsolutePath().toString(), p.getSourcePath().toString()))
            .toList();

    if (!j2cl.transpile(javaSources, nativeSources)) {
      throw new RuntimeException("J2CL transpilation failed");
    }

  }

  private List<Dependency> getAllDependencies() {
    Set<Dependency> processed = new HashSet<>();
    List<Dependency> result = new ArrayList<>();
    Queue<Dependency> queue = new LinkedList<>(dependency.getDependencies());
    while (!queue.isEmpty()) {
      Dependency current = queue.poll();
      if (!processed.contains(current)) {
        result.add(current);
        processed.add(current);
        queue.addAll(current.getDependencies());
      }
    }
    return result;
  }
}
