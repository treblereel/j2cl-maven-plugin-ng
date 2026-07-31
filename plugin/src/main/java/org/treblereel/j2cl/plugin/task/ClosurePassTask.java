package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.xbt.XtbResolver;

public class ClosurePassTask extends TaskInput {

    public ClosurePassTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.CLOSURE_PASS;
    }

    @Override
    public void process() {
        TaskOutput transpiled = input(dependency, OutputTypes.TRANSPILED_JS);

        java.util.List<File> xtbFiles = XtbResolver.resolveTranslationsFiles(
                buildContext.getConfig().translationsFile(), buildContext.getConfig().defines(),
                buildContext.getProjectBaseDir(), buildContext.getAdditionalXtbSearchPaths(), logger);
        if (xtbFiles.isEmpty()) {
            copyAllFiles(transpiled);
            return;
        }

        for (FileEntry fe : transpiled.files()) {
            Path targetPath = outputPath().resolve(fe.getSourcePath());
            try {
                Files.createDirectories(targetPath.getParent());

                String sourcePath = fe.getSourcePath().toString();
                if (!sourcePath.endsWith(".js") || sourcePath.endsWith(".native.js")) {
                    Files.copy(fe.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }

                boolean isBundleJar = "BUNDLE_JAR".equalsIgnoreCase(buildContext.getConfig().compilationLevel());
                String content = Files.readString(fe.getAbsolutePath(), StandardCharsets.UTF_8);
                if (isBundleJar) {
                    String translated = runClosure(content, fe.getSourcePath().toString(), xtbFiles);
                    Files.writeString(targetPath, translated, StandardCharsets.UTF_8);
                } else {
                    Files.copy(fe.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to process file for translation: " + fe.getSourcePath(), e);
            }
        }
    }

    private String runClosure(String source, String name,
                                      java.util.List<File> xtbFiles) {
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
        options.setClosurePass(false);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.NO_TRANSPILE);
        options.setApplyInputSourceMaps(false);
        Object locale = buildContext.getConfig().defines().get("goog.LOCALE");
        XtbResolver.applyXtbFiles(options, xtbFiles, locale != null ? locale.toString() : null, logger);

        Compiler compiler = new Compiler();
        compiler.disableThreads();
        List<SourceFile> inputs = List.of(SourceFile.fromCode(name, source));
        Result result = compiler.compile(List.of(), inputs, options);

        if (result.errors.size() > 0) {
            logger.warn("Translation errors in " + name + ": " + result.errors.size());
            for (var error : result.errors) {
                logger.warn("  " + error.toString());
            }
            return source;
        }
        return compiler.toSource();
    }

    private void copyAllFiles(TaskOutput source) {
        for (FileEntry fe : source.files()) {
            Path targetPath = outputPath().resolve(fe.getSourcePath());
            try {
                Files.createDirectories(targetPath.getParent());
                Files.copy(fe.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy file: " + fe.getSourcePath(), e);
            }
        }
    }
}
