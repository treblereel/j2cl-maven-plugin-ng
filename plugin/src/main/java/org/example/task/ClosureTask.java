package org.example.task;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DependencyOptions;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.tools.Closure;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.StreamSupport;

public class ClosureTask extends TaskInput {

  private static final Path META_INF = Paths.get("META-INF");
  /**
   * servlet 3 and webjars convention
   */
  private static final Path META_INF_RESOURCES = META_INF.resolve("resources");
  /**
   * optional directory to offer externs within a jar
   */
  private static final Path META_INF_EXTERNS = META_INF.resolve("externs");

  private static final Path PUBLIC = Paths.get("public");

  private static final PathMatcher JS_SOURCES = withSuffix(".js");

  private static final PathMatcher XTB = withSuffix(".xtb");
  private static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");
  private static final PathMatcher EXTERNS_SOURCES = withSuffix(".externs.js");

  private static final PathMatcher IN_META_INF = path -> path.startsWith(META_INF);
  private static final PathMatcher IN_META_INF_EXTERNS = path -> path.startsWith(META_INF_EXTERNS);
  private static final PathMatcher IN_META_INF_RESOURCES = path -> path.startsWith(META_INF_RESOURCES);

  private static final PathMatcher IN_PUBLIC = path -> StreamSupport.stream(path.spliterator(), false).anyMatch(PUBLIC::equals);

  /**
   * JS files that closure should use as type information
   */
  private static final PathMatcher EXTERNS = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      return IN_META_INF_EXTERNS.matches(path) || EXTERNS_SOURCES.matches(path);
    }

    @Override
    public String toString() {
      return "externs to pass to closure";
    }
  };

  /**
   * JS files that closure should accept as input to bundle/compile.
   */
  private static final PathMatcher PLAIN_JS_SOURCES = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      if (IN_META_INF.matches(path) && !IN_META_INF_EXTERNS.matches(path)) {
        return false;
      }
      if (IN_PUBLIC.matches(path)) {
        return false;
      }
      return JS_SOURCES.matches(path) && !NATIVE_JS_SOURCES.matches(path) && !EXTERNS.matches(path);
    }

    @Override
    public String toString() {
      return "Only non-native JS sources";
    }
  };

  public ClosureTask(Dependency dep, BuildContext buildContext) {
    super(dep, buildContext);
  }

  @Override
  public OutputTypes getOutputTypes() {
    return OutputTypes.OPTIMIZED_JS;
  }

  @Override
  public void process() {

    //TaskOutput self = input(dependency, OutputTypes.TRANSPILED_JS);
    //TaskOutput selfUnzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);

    List<Dependency> allDependencies = getAllDependencies();
    TaskOutput depsUnzipped = input(allDependencies, OutputTypes.UNZIPPED_DEPENDENCIES);
    TaskOutput depsTranspiled = input(allDependencies, OutputTypes.TRANSPILED_JS);

    Map<String, List<String>> js = Closure.mapFromInputs(depsTranspiled.files().stream()
            .filter(f -> PLAIN_JS_SOURCES.matches(f.getSourcePath()))
            .toList());


    js.forEach((k, v) -> {
      System.out.println("From transpiled: " + k);
      v.forEach(f -> System.out.println("  " + f));
    });




    Closure closureCompiler = new Closure((BuildLog) buildContext.getConfig());


    closureCompiler.compile(
            CompilationLevel.ADVANCED_OPTIMIZATIONS,
            DependencyOptions.DependencyMode.PRUNE,
            CompilerOptions.LanguageMode.ECMASCRIPT_NEXT,
            js,
            outputPath().toFile(),
            List.of(),
            Map.of(),
            List.of(),
            Optional.empty(),
            true,
            true,
            true,
            false,
            "BROWSER",
            "test.js"
            );


/*        List<Dependency> allDependencies = getAllDependencies();
        TaskOutput transpiled = input(allDependencies, OutputTypes.TRANSPILED_JS);
        TaskOutput sources = input(allDependencies, OutputTypes.TRANSPILED_JS);

        Stream<FileEntry> asStream = Stream.concat(
                transpiled.files().stream(),
                sources.files().stream()
        );

        asStream.forEach(f -> {
            System.out.println();
        });*/


  }


  private List<Dependency> getAllDependencies() {
    Set<Dependency> processed = new HashSet<>();
    List<Dependency> result = new ArrayList<>();
    Queue<Dependency> queue = new LinkedList<>();
    queue.add(dependency);
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
