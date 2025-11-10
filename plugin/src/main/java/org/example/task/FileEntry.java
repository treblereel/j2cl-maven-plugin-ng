package org.example.task;

import org.example.model.Dependency;

import java.nio.file.Path;

public class FileEntry {

    private final Path sourcePath;
    private final Path absolutePath;
    private final Dependency dependency;

    public FileEntry(Path sourcePath, Path absolutePath, Dependency dependency) {
        this.sourcePath = sourcePath;
        this.absolutePath = absolutePath;
        this.dependency = dependency;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public Dependency getDependency() {
        return dependency;
    }
}
