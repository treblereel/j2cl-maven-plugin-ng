package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;
import org.example.utils.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;

public class UnzipTaskInput extends TaskInput{

    public UnzipTaskInput(Dependency dep, BuildContext buildContext) {
        super(dep, buildContext);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.UNZIPPED_DEPENDENCIES;
    }

    @Override
    public Runnable process() {
        return () -> {
            insureOutputDirectoryExists();
            outputPath().toFile().mkdirs();
            if (!outputPath().toFile().exists()) {
                throw new RuntimeException(String.format("Unable to create output path %s", outputPath()));
            }
            if(!dep.isSourceMapped()) {
                File jar = dep.sourcesJar();
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
                    throw new RuntimeException("Failed to unzip file " + jar + " at dependency " + dep.key(), e);
                }
            } else {
                try {
                    System.out.println("Copying sources from " + dep.sourcesJar() + " to " + outputPath());
                    FileUtils.copyDirectory(dep.sourcesJar().toPath(), outputPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println(String.format("Unzipping %s to %s", key(), buildContext.getOutputDirectory()));
        };
    }
}
