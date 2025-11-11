package org.example.task;

import org.example.context.BuildContext;
import org.example.model.Dependency;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
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
            if(!dep.isSourceMapped()) {
                insureOutputDirectoryExists();
                outputPath().toFile().mkdirs();
                if (!outputPath().toFile().exists()) {
                    throw new RuntimeException(String.format("Unable to create output path %s", outputPath()));
                }

                File jar = dep.resolve();
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
            }
            System.out.println(String.format("Unzipping %s to %s", key(), buildContext.getOutputDirectory()));
        };
    }
}
