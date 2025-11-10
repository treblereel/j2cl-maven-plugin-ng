package org.example.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TaskOutput {

    private final List<Path> output;
    private final TaskInput taskInput;

    public TaskOutput(List<Path> output, TaskInput taskInput) {
        this.output = output;
        this.taskInput = taskInput;
    }

    public TaskOutput filter() {
        return new FilteredOutput(this);
    }

    public List<FileEntry> files() {
        Path outputPath = taskInput.outputPath();
        try {
            return Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .map(p -> new FileEntry(p, outputPath, taskInput.dep))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected class FilteredOutput extends TaskOutput {

        private final PathMatcher[] filters;
        private final TaskOutput wrapped;

        public FilteredOutput(TaskOutput output, PathMatcher... filters) {
            super(output.output, output.taskInput);
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
                    .filter(entry -> Arrays.stream(filters).anyMatch(f -> f.matches(entry.getSourcePath())))
                    .toList();
        }
    }
}
