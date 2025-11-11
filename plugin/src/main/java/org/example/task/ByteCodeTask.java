package org.example.task;

import com.google.common.collect.ImmutableList;
import com.google.turbine.diag.TurbineError;
import com.google.turbine.main.Main;
import com.google.turbine.options.LanguageVersion;
import com.google.turbine.options.TurbineOptions;
import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ByteCodeTask extends TaskInput {

  public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
  public static final PathMatcher JAVA_BYTECODE = withSuffix(".class");
  public static final PathMatcher NOT_BYTECODE = p -> !JAVA_BYTECODE.matches(p);
  public static final PathMatcher JAVA_MODULE_INFO = withSuffix("module-info.java");
  public static final PathMatcher TURBINE_OUTPUT = filename("output.jar");
  public static final PathMatcher JAVA_SOURCES_EXCEPT_MODULE_INFO = p -> JAVA_SOURCES.matches(p) && !JAVA_MODULE_INFO.matches(p);

  public static final PathMatcher APT_PROCESSOR = p ->
          p.equals(Paths.get("META-INF", "services", "javax.annotation.processing.Processor"));

  public ByteCodeTask(Dependency dep, BuildContext buildContext) {
    super(dep, buildContext);
  }

  public OutputTypes getOutputTypes() {
    return OutputTypes.BYTECODE;
  }

  @Override
  public Runnable process() {
    return () -> {
      insureOutputDirectoryExists();
      outputPath().toFile().mkdirs();
      if (!outputPath().toFile().exists()) {
        throw new RuntimeException(String.format("Unable to create output path %s", outputPath()));
      }

      List<File> extraClasspath = buildContext.getConfig().getExtraClasspath();


      TaskOutput self = input(dep, OutputTypes.UNZIPPED_DEPENDENCIES).filter(JAVA_SOURCES);


      Set<String> deps = Stream.concat(extraClasspath.stream().map(File::toString),
                      input(dep.getDependencies(), OutputTypes.BYTECODE)
                              .filter(TURBINE_OUTPUT)
                              .files()
                              .stream()
                              .map(f -> f.getAbsolutePath().toFile().toString()))
              .collect(Collectors.toSet());

      File output = new File(outputPath().toFile(), "output.jar");

      try {

        input(dep.getDependencies(), OutputTypes.BYTECODE)
                .filter(TURBINE_OUTPUT)
                .files()
                .stream()
                .map(f -> f.getAbsolutePath().toFile().toString()).forEach(s -> System.out.println("Classpath entry: " + s));

        Main.Result result = Main.compile(
                TurbineOptions.builder()
                        //.setDirectJars()
                        .setSources(ImmutableList.copyOf(self.files().stream().map(f -> f.getAbsolutePath().toFile().toString()).toList()))
                        .setOutput(output.toString())
                        .setClassPath(ImmutableList.copyOf(deps))
                        .setLanguageVersion(LanguageVersion.fromJavacopts(
                                ImmutableList.of("-source", "21", "-target", "21", "--release", "21")))
                        //TODO https://github.com/Vertispan/j2clmavenplugin/issues/181
                        //.setReducedClasspathMode(TurbineOptions.ReducedClasspathMode.JAVABUILDER_REDUCED)
                        .build());

        //TODO run APT and write output .java files to sources and resources to sources as well
      } catch (TurbineError e) {
        throw new RuntimeException("Turbine compilation failed at dependency " + dep.key(), e);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }


      System.out.println(String.format("Processing %s", key()));
    };
  }


}
