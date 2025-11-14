package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;
import org.example.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public class UnzipTaskInput extends TaskInput {

  public UnzipTaskInput(Dependency dep, BuildContext buildContext) {
    super(dep, buildContext);
  }

  @Override
  public OutputTypes getOutputTypes() {
    return OutputTypes.UNZIPPED_DEPENDENCIES;
  }

  @Override
  public void process() {
    if (!dependency.isSourceMapped()) {
      File jar = dependency.bytecodeJar();
      try (ZipFile zipFile = new ZipFile(jar)) {
        zipFile.stream().forEach(entry -> {
          try {
            File outFile = outputPath().resolve(entry.getName()).toFile();
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
