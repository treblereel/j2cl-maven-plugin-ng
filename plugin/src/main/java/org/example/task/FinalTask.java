package org.example.task;

import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.model.Dependency;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class FinalTask extends TaskInput {

    private static final PathMatcher JS_SOURCES = withSuffix(".js");

    public FinalTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.FINAL_TASK;
    }

    @Override
    public void process() {
        TaskOutput output = input(dependency, OutputTypes.OPTIMIZED_JS);
        TaskOutput depsUnzipped = input(getFlattenDependencies(), OutputTypes.UNZIPPED_DEPENDENCIES);

        Path webappDirectory = Paths.get(buildContext.getConfig().webappDirectory());
        try {
            Files.createDirectories(webappDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create the webappDirectory " + buildContext.getConfig().webappDirectory(), e);
        }

        try {
            List<FileEntry> files = output.files();
            for (FileEntry fileEntry : files) {
                Files.createDirectories(webappDirectory.resolve(fileEntry.getSourcePath()).getParent());
                Files.copy(fileEntry.getAbsolutePath(), webappDirectory.resolve(fileEntry.getSourcePath()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy sources for dependency " + dependency.key(), e);
        }
    }
}
