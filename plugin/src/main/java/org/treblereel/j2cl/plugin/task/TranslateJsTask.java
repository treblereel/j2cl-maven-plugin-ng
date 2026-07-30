package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.xbt.TranslationsFileConfig;
import org.treblereel.j2cl.plugin.xbt.XtbResolver;

public class TranslateJsTask extends TaskInput {

    public TranslateJsTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.TRANSLATED_JS;
    }

    @Override
    public void process() {
        TaskOutput transpiled = input(dependency, OutputTypes.TRANSPILED_JS);

        TranslationsFileConfig tf = buildContext.getConfig().translationsFile();
        if (tf == null) {
            copyAllFiles(transpiled);
            return;
        }

        File xtbFile = XtbResolver.resolveTranslationsFile(
                tf, buildContext.getConfig().defines(),
                buildContext.getProjectBaseDir(), logger);
        if (xtbFile == null) {
            copyAllFiles(transpiled);
            return;
        }

        for (FileEntry fe : transpiled.files()) {
            Path targetPath = outputPath().resolve(fe.getSourcePath());
            try {
                Files.createDirectories(targetPath.getParent());

                String content = Files.readString(fe.getAbsolutePath(), StandardCharsets.UTF_8);
                if (content.contains("goog.getMsg")) {
                    String translated = applyTranslations(content, fe.getSourcePath().toString(), tf, xtbFile);
                    Files.writeString(targetPath, translated, StandardCharsets.UTF_8);
                } else {
                    Files.copy(fe.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to process file for translation: " + fe.getSourcePath(), e);
            }
        }
    }

    private String applyTranslations(String source, String name,
                                      TranslationsFileConfig tf, File xtbFile) {
        CompilerOptions options = new CompilerOptions();
        options.setSkipNonTranspilationPasses(false);
        options.setClosurePass(false);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.NO_TRANSPILE);
        XtbResolver.applyXtbFile(options, tf, xtbFile, logger);

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
