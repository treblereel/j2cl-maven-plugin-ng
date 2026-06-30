package org.treblereel.j2cl.plugin.utils;

import org.treblereel.j2cl.plugin.model.Dependency;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.zip.ZipFile;

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
    if (Files.exists(path)) {
      try (var walk = Files.walk(path)) {
        walk.sorted(Comparator.reverseOrder())
                .forEach(subpath -> {
                  try {
                    Files.delete(subpath);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
      } catch (IOException e) {
        throw new RuntimeException("Failed to delete directory " + path, e);
      }
    }
  }
}
