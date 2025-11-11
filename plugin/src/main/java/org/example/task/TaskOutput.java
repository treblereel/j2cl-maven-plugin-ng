package org.example.task;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public class TaskOutput {

    private final List<Path> output;

    public TaskOutput(List<Path> output) {
        this.output = output;
    }

    public TaskOutput filter(PathMatcher... filters) {
        return new FilteredOutput(this, filters);
    }

    public List<FileEntry> files() {
        List<FileEntry> result = new ArrayList<>();
        for (Path outputPath : output) {
            if (!Files.exists(outputPath)) continue;
            try (Stream<Path> stream = Files.walk(outputPath)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> new FileEntry(outputPath.relativize(p), p, outputPath))
                        .forEach(result::add);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    protected class FilteredOutput extends TaskOutput {

        private final PathMatcher[] filters;
        private final TaskOutput wrapped;

        public FilteredOutput(TaskOutput output, PathMatcher... filters) {
            super(output.output);
            this.wrapped = output;
            this.filters = filters;
        }

        public TaskOutput filter(PathMatcher... filters) {
            HashSet<PathMatcher> allMatchers = new HashSet<>(Arrays.asList(this.filters));
            allMatchers.addAll(Arrays.asList(filters));
            return new FilteredOutput(wrapped, allMatchers.toArray(filters));
        }

        public List<FileEntry> files() {
            List<FileEntry> files = super.files();
            return files.stream()
                    .filter(entry -> Files.isRegularFile(entry.getAbsolutePath()))
                    .filter(entry -> Arrays.stream(filters).anyMatch(f -> f.matches(entry.getSourcePath())))
                    .toList();
        }
    }
}
