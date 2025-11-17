package org.example.task;

import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;
import org.example.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

public class UnzipTaskInput extends TaskInput {

    public UnzipTaskInput(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.UNZIPPED_DEPENDENCIES;
    }

    @Override
    public void process() {
        if (!dependency.isSourceMapped()) {
            FileUtils.extractZip(dependency.bytecodeJar(), outputPath(), dependency);
        } else {
            try {
                Path sourcePath = dependency.asMavenProject().getBasedir().toPath().resolve("src/main/java");
                Path resourcePath = dependency.asMavenProject().getBasedir().toPath().resolve("src/main/resources");

                FileUtils.copyDirectory(sourcePath, outputPath());
                FileUtils.copyDirectory(resourcePath, outputPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy sources for dependency " + dependency.key(), e);
            }
        }
    }

}
