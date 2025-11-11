package org.example.task;

import com.google.j2cl.common.SourceUtils;
import org.example.context.BuildContext;
import org.example.model.Dependency;
import org.example.utils.GwtIncompatiblePreprocessor;

import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

public class StripSourcesTask extends TaskInput {

  public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
  public static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");

  public static final PathMatcher OUTPUT_JAR = withSuffix("output.jar");


  public StripSourcesTask(Dependency dep, BuildContext buildContext) {
    super(dep, buildContext);
  }

  @Override
  public OutputTypes getOutputTypes() {
    return OutputTypes.STRIPPED_SOURCES;
  }

  @Override
  public void process() {
    TaskOutput bytecode = input(dep, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES, NATIVE_JS_SOURCES);
    TaskOutput sources = input(dep, OutputTypes.UNZIPPED_DEPENDENCIES).filter(JAVA_SOURCES);
    TaskOutput headers = input(dep, OutputTypes.STRIPPED_BYTECODE).filter(OUTPUT_JAR);

    Stream.concat(bytecode.files().stream(), sources.files().stream())
            .

    List<SourceUtils.FileInfo> files = Stream.concat(bytecode.files().stream(), sources.files().stream())
            .map(f -> SourceUtils.FileInfo.create(f.getAbsolutePath().toString(), f.getSourcePath().toString())).toList();

    GwtIncompatiblePreprocessor preprocessor = new GwtIncompatiblePreprocessor(outputPath().toFile());
    preprocessor.preprocess(files);
  }
}
