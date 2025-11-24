package org.example.task;

import java.nio.file.Path;

public class FileEntry {

    private final Path sourcePath;
    private final Path absolutePath;
    private final Path parentPath;

    public FileEntry(Path sourcePath, Path absolutePath, Path parentPath) {
        this.sourcePath = sourcePath;
        this.absolutePath = absolutePath;
        this.parentPath = parentPath;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public Path getParentPath() {
        return parentPath;
    }

}
