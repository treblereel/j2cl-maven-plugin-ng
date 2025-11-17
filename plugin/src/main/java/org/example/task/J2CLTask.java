package org.example.task;

import com.google.j2cl.common.SourceUtils;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.tools.J2cl;

import java.io.File;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

public class J2CLTask extends TaskInput {

    public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
    public static final PathMatcher NATIVE_JS_SOURCES = withSuffix(".native.js");
    public static final PathMatcher TURBINE_OUTPUT = withSuffix("output.jar");

    public J2CLTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.TRANSPILED_JS;
    }

    @Override
    public void process() {
        TaskOutput self = input(dependency, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES);

        Stream<FileEntry> selfNative = Stream.concat(
                input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES).filter(NATIVE_JS_SOURCES).files().stream(),
                input(dependency, OutputTypes.BYTECODE).filter(NATIVE_JS_SOURCES).files().stream()
        );

        List<File> moduleDependencies = Stream.concat(
                        buildContext.getConfig().getExtraClasspath().stream().map(File::toString),
                        input(getFlattenDependencies(), OutputTypes.BYTECODE)
                                .filter(TURBINE_OUTPUT)
                                .files()
                                .stream()
                                .map(f -> f.getAbsolutePath().toFile().toString()))
                .distinct()
                .map(File::new)
                .toList();

        File bootstrapClasspath = buildContext.getConfig().getBootstrapClasspath();

        J2cl j2cl = new J2cl(moduleDependencies,
                bootstrapClasspath,
                outputPath().toFile(),
                logger);

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
}
