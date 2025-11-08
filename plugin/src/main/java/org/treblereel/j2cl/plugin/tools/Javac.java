package org.treblereel.j2cl.plugin.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.treblereel.j2cl.plugin.log.BuildLog;

public class Javac {

    private final BuildLog log;
    private final List<String> javacOptions;
    private final JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;
    private final DiagnosticCollector<JavaFileObject> listener;

    public Javac(BuildLog log, File generatedSourcesDir, List<File> sourcePaths,
                 List<File> classpath, File classOutputDir, File bootstrapClasspath,
                 List<String> processorNames, List<String> javacOpts) throws IOException {
        this.log = log;

        javacOptions = new ArrayList<>();
        javacOptions.add("-encoding");
        javacOptions.add("utf8");
        javacOptions.add("-implicit:none");
        javacOptions.add("--release");
        javacOptions.add("21");

        if (generatedSourcesDir == null) {
            javacOptions.add("-proc:none");
        } else if (!processorNames.isEmpty()) {
            javacOptions.add("-processor");
            javacOptions.add(String.join(",", processorNames));
        }

        javacOptions.addAll(javacOpts);

        compiler = ToolProvider.getSystemJavaCompiler();
        listener = new DiagnosticCollector<>();
        fileManager = compiler.getStandardFileManager(listener, null, null);

        fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePaths);
        fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(classOutputDir));

        if (bootstrapClasspath != null) {
            fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH,
                    Collections.singleton(bootstrapClasspath));
        }

        if (generatedSourcesDir != null) {
            generatedSourcesDir.mkdirs();
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT,
                    Collections.singleton(generatedSourcesDir));
        }
    }

    public boolean compile(List<String> sourceFiles) {
        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromStrings(sourceFiles);

        CompilationTask task = compiler.getTask(
                null, fileManager, listener, javacOptions, null, compilationUnits);

        try {
            return task.call();
        } finally {
            listener.getDiagnostics().forEach(d -> {
                String message = d.getMessage(Locale.getDefault());
                JavaFileObject source = d.getSource();
                if (source != null) {
                    String path = source.toUri().getPath();
                    String prefix = path
                            + (d.getLineNumber() > 0 ? ":" + d.getLineNumber() : "") + ": ";
                    message = prefix + message;
                }
                switch (d.getKind()) {
                    case ERROR -> log.error("Error:" + message);
                    case WARNING, MANDATORY_WARNING -> log.warn(message);
                    case NOTE -> log.info(message);
                    case OTHER -> log.debug(message);
                }
            });
        }
    }
}
