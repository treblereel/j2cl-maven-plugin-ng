package org.example.task;

import org.example.model.Dependency;

import java.nio.file.Path;

public class FileEntry {

    private final Path sourcePath;
    private final Path absolutePath;
    public FileEntry(Path sourcePath, Path absolutePath, Path parentPath) {
        this.sourcePath = sourcePath;
        this.absolutePath = absolutePath;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

}
