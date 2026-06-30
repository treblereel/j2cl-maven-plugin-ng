package org.treblereel.j2cl.plugin.task;

import com.google.j2cl.common.SourceUtils;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.GwtIncompatiblePreprocessor;

import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

public class StripSourcesTask extends TaskInput {

    public static final PathMatcher JAVA_SOURCES = withSuffix(".java");

    public StripSourcesTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.STRIPPED_SOURCES;
    }

    @Override
    public void process() {
        TaskOutput bytecode = input(dependency, OutputTypes.BYTECODE).filter(JAVA_SOURCES);
        TaskOutput sources = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES).filter(JAVA_SOURCES);

        List<SourceUtils.FileInfo> files = Stream.concat(
                        bytecode.files().stream(),
                        sources.files().stream()
                ).map(f -> SourceUtils.FileInfo.create(f.getAbsolutePath().toString(), f.getSourcePath().toString()))
                .toList();

        GwtIncompatiblePreprocessor preprocessor = new GwtIncompatiblePreprocessor(outputPath().toFile(), logger);
        preprocessor.preprocess(files);
    }
}
