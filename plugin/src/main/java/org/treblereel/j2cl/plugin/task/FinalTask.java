package org.treblereel.j2cl.plugin.task;

import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FinalTask extends TaskInput {

    private static final PathMatcher JS_SOURCES = withSuffix(".js");

    public FinalTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.FINAL_TASK;
    }

    @Override
    public void process() {
        TaskOutput output = input(dependency, OutputTypes.OPTIMIZED_JS);
        TaskOutput selfTranspiled = input(dependency, OutputTypes.TRANSPILED_JS);
        TaskOutput depsTranspiled = input(getFlattenDependencies(), OutputTypes.TRANSPILED_JS);

        Path webappDirectory = Paths.get(buildContext.getConfig().webappDirectory());
        try {
            Files.createDirectories(webappDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create the webappDirectory " + buildContext.getConfig().webappDirectory(), e);
        }

        try {
            List<FileEntry> files = output.files();
            for (FileEntry fileEntry : files) {
                Files.createDirectories(webappDirectory.resolve(fileEntry.getSourcePath()).getParent());
                Files.copy(fileEntry.getAbsolutePath(), webappDirectory.resolve(fileEntry.getSourcePath()), StandardCopyOption.REPLACE_EXISTING);
            }

            if (buildContext.getConfig().enableSourcemaps()) {
                String scriptFilename = buildContext.getConfig().initialScriptFilename();
                Path jsFile = webappDirectory.resolve(scriptFilename);
                Path sourceMapDir = jsFile.getParent().resolve("sources");
                Files.createDirectories(sourceMapDir);

                Stream.concat(selfTranspiled.files().stream(), depsTranspiled.files().stream())
                        .forEach(fe -> {
                            try {
                                Path targetPath = sourceMapDir.resolve(fe.getSourcePath());
                                Files.createDirectories(targetPath.getParent());
                                Files.copy(fe.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to copy source file " + fe.getSourcePath(), e);
                            }
                        });

                for (File zipFile : buildContext.getConfig().getJsZip()) {
                    extractZipSources(zipFile, sourceMapDir);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to copy sources for dependency " + dependency.key(), e);
        }
    }

    private void extractZipSources(File zipFile, Path sourceMapDir) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile)) {
            String commonPrefix = findCommonPrefix(zf);
            if (commonPrefix == null) return;
            String prefix = commonPrefix.isEmpty() ? "" : commonPrefix + "/";

            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(prefix)) continue;
                String relative = name.substring(prefix.length());
                Path targetPath = sourceMapDir.resolve(relative);
                Files.createDirectories(targetPath.getParent());
                try (InputStream is = zf.getInputStream(entry)) {
                    Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String findCommonPrefix(ZipFile zf) {
        String commonDir = null;
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            String dir = name.contains("/") ? name.substring(0, name.lastIndexOf('/')) : "";
            if (commonDir == null) {
                commonDir = dir;
            } else {
                while (!commonDir.isEmpty() && !dir.startsWith(commonDir)) {
                    int lastSlash = commonDir.lastIndexOf('/');
                    commonDir = lastSlash >= 0 ? commonDir.substring(0, lastSlash) : "";
                }
            }
        }
        return commonDir;
    }
}
