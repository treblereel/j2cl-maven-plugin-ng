package org.treblereel.j2cl.plugin.xbt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.XtbMessageBundle;
import org.treblereel.j2cl.plugin.log.BuildLog;

public class XtbResolver {

    public static void applyTranslations(CompilerOptions options, TranslationsFileConfig tf,
                                          Map<String, Object> defines, File projectBaseDir, BuildLog log) {
        if (tf == null) return;

        File xtbFile = resolveTranslationsFile(tf, defines, projectBaseDir, log);
        if (xtbFile == null) return;

        applyXtbFile(options, tf, xtbFile, log);
    }

    public static void applyXtbFile(CompilerOptions options, TranslationsFileConfig tf,
                                     File xtbFile, BuildLog log) {
        log.info("Using translations file: " + xtbFile.getAbsolutePath());
        try (FileInputStream is = new FileInputStream(xtbFile)) {
            InputStream source = tf.isAuto()
                    ? XtbPreprocessor.preprocess(is)
                    : is;
            options.setMessageBundle(new XtbMessageBundle(source, null));
            if (source != is) source.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read translations file: " + xtbFile, e);
        }
    }

    public static File resolveTranslationsFile(TranslationsFileConfig tf, Map<String, Object> defines,
                                                File projectBaseDir, BuildLog log) {
        if (tf.getFile() != null && !tf.getFile().isEmpty()) {
            File f = new File(tf.getFile());
            if (!f.exists()) {
                throw new RuntimeException("Translations file not found: " + f.getAbsolutePath());
            }
            return f;
        }
        if (tf.isAuto()) {
            Object locale = defines.get("goog.LOCALE");
            if (locale == null) {
                log.warn("translationsFile auto=true but goog.LOCALE not set in defines");
                return null;
            }
            return findXtbByLocale(locale.toString(), projectBaseDir, log);
        }
        return null;
    }

    public static File findXtbByLocale(String locale, File baseDir, BuildLog log) {
        String normalizedLocale = locale.replace("-", "_");
        java.util.regex.Pattern localePattern = java.util.regex.Pattern.compile(
                "[_\\-.](" + java.util.regex.Pattern.quote(locale)
                        + "|" + java.util.regex.Pattern.quote(normalizedLocale)
                        + ")[_\\-.]");

        Path[] searchRoots = {
                baseDir.toPath(),
                baseDir.toPath().resolve("src/main/java"),
                baseDir.toPath().resolve("src/main/resources")
        };

        for (Path root : searchRoots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                Optional<Path> match = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".xtb"))
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return localePattern.matcher(name).find();
                        })
                        .findFirst();
                if (match.isPresent()) {
                    return match.get().toFile();
                }
            } catch (IOException e) {
                log.warn("Failed to scan for XTB files in " + root + ": " + e.getMessage());
            }
        }
        log.warn("No XTB file found for locale '" + locale + "' in " + baseDir);
        return null;
    }
}
