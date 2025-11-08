package org.treblereel.j2cl.plugin.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
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
      zipFile.stream().forEach(entry -> {
        try {
          File outFile = outputPath.resolve(entry.getName()).toFile();
          if (entry.isDirectory()) {
            outFile.mkdirs();
          } else {
            outFile.getParentFile().mkdirs();
            try (InputStream is = zipFile.getInputStream(entry);
                 OutputStream os = new FileOutputStream(outFile)) {
              is.transferTo(os);
            }
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    } catch (Exception e) {
      throw new RuntimeException("Failed to unzip file " + jar + " at dependency " + dependency.key(), e);
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
