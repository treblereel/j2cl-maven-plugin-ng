package org.treblereel.j2cl.plugin.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.treblereel.j2cl.plugin.model.Dependency;

public class FileUtils {

  public static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
    Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(dir));
        if (!Files.exists(targetPath)) {
          Files.createDirectories(targetPath);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(file));
        Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static void extractZip(File jar, Path outputPath, Dependency dependency) {
    try (ZipFile zipFile = new ZipFile(jar)) {
      var entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        extractZipEntry(zipFile, entry, outputPath, entry.getName());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to unzip file " + jar + " at dependency " + dependency.key(), e);
    }
  }

  public static final class UnsafeArchiveEntryException extends IOException {
    public UnsafeArchiveEntryException(String name) {
      super("Unsafe archive entry: " + name);
    }
  }

  public static void extractZipEntry(ZipFile zip, ZipEntry entry, Path outputPath,
                                     String relativeName) throws IOException {
    // Validate both the original name and any caller-stripped prefix representation.
    validateArchivePath(entry.getName());
    Path relative = validateArchivePath(relativeName);
    Files.createDirectories(outputPath);
    Path root = outputPath.toRealPath();
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root) || (target.equals(root) && !entry.isDirectory())) {
      throw new UnsafeArchiveEntryException(entry.getName());
    }
    if (target.equals(root)) return;
    Path directories = entry.isDirectory() ? target : target.getParent();
    Path current = root;
    for (Path part : root.relativize(directories)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) {
        throw new UnsafeArchiveEntryException(entry.getName());
      }
      if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        Files.createDirectory(current);
      }
    }
    if (!entry.isDirectory()) {
      if (Files.isSymbolicLink(target)) {
        throw new UnsafeArchiveEntryException(entry.getName());
      }
      try (InputStream is = zip.getInputStream(entry);
           OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE,
                   StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
        is.transferTo(os);
      }
    }
  }

  private static Path validateArchivePath(String name) throws UnsafeArchiveEntryException {
    // ZIP paths use '/', regardless of the host OS. Reject Windows aliases on every platform.
    if (name.isEmpty() || name.startsWith("/") || name.contains("\\")
            || name.matches("^[a-zA-Z]:.*")) {
      throw new UnsafeArchiveEntryException(name);
    }
    try {
      Path path = Path.of(name);
      if (path.isAbsolute()) throw new UnsafeArchiveEntryException(name);
      for (Path part : path) {
        if (part.toString().equals("..")) throw new UnsafeArchiveEntryException(name);
      }
      return path;
    } catch (InvalidPathException e) {
      throw new UnsafeArchiveEntryException(name);
    }
  }

  public static void deleteDirectoryRecursive(Path path) {
    if (!Files.exists(path)) return;
    try {
      Files.walkFileTree(path, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.deleteIfExists(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete directory " + path, e);
    }
  }
}
