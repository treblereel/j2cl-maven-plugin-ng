package org.treblereel.j2cl.plugin.xbt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.XtbMessageBundle;
import org.treblereel.j2cl.plugin.log.BuildLog;

public class XtbResolver {

    public static void applyTranslations(CompilerOptions options, TranslationsFileConfig tf,
                                          Map<String, Object> defines, File projectBaseDir,
                                          List<Path> additionalSearchPaths, BuildLog log) {
        List<File> xtbFiles = resolveTranslationsFiles(tf, defines, projectBaseDir, additionalSearchPaths, log);
        if (xtbFiles.isEmpty()) return;

        String locale = defines != null ? (String) defines.get("goog.LOCALE") : null;
        applyXtbFiles(options, xtbFiles, locale, log);
    }

    public static void applyXtbFiles(CompilerOptions options, List<File> xtbFiles,
                                      String locale, BuildLog log) {
        for (File f : xtbFiles) {
            log.info("Using translations file: " + f.getAbsolutePath());
        }

        if (xtbFiles.size() == 1) {
            try (FileInputStream is = new FileInputStream(xtbFiles.get(0))) {
                InputStream source = XtbPreprocessor.preprocess(is, locale);
                options.setMessageBundle(new XtbMessageBundle(source, null));
                source.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to read translations file: " + xtbFiles.get(0), e);
            }
        } else {
            List<InputStream> streams = new ArrayList<>();
            try {
                for (File f : xtbFiles) {
                    streams.add(new FileInputStream(f));
                }
                InputStream merged = XtbPreprocessor.merge(streams, locale);
                options.setMessageBundle(new XtbMessageBundle(merged, null));
                merged.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to merge translations files", e);
            } finally {
                for (InputStream is : streams) {
                    try { is.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    public static List<File> resolveTranslationsFiles(TranslationsFileConfig tf, Map<String, Object> defines,
                                                       File projectBaseDir, List<Path> additionalSearchPaths,
                                                       BuildLog log) {
        List<File> result = new ArrayList<>();

        if (tf != null && tf.getFile() != null && !tf.getFile().isEmpty()) {
            File f = new File(tf.getFile());
            if (!f.exists()) {
                throw new RuntimeException("Translations file not found: " + f.getAbsolutePath());
            }
            result.add(f);
        }

        Object locale = defines != null ? defines.get("goog.LOCALE") : null;
        boolean explicitAuto = tf != null && tf.isAuto();
        boolean implicitAuto = tf == null && locale != null;
        if (explicitAuto || implicitAuto) {
            List<File> autoFiles;
            if (locale != null) {
                autoFiles = findAllXtbByLocale(locale.toString(), projectBaseDir,
                        additionalSearchPaths, log);
            } else {
                autoFiles = findAllXtb(projectBaseDir, additionalSearchPaths, log);
            }
            for (File af : autoFiles) {
                if (!result.contains(af)) {
                    result.add(af);
                }
            }
        }

        return result;
    }

    @Deprecated
    public static File resolveTranslationsFile(TranslationsFileConfig tf, Map<String, Object> defines,
                                                File projectBaseDir, BuildLog log) {
        List<File> files = resolveTranslationsFiles(tf, defines, projectBaseDir, Collections.emptyList(), log);
        return files.isEmpty() ? null : files.get(0);
    }

    public static List<File> findAllXtb(File baseDir, List<Path> additionalSearchPaths, BuildLog log) {
        List<Path> searchRoots = buildSearchRoots(baseDir, additionalSearchPaths);
        List<File> found = new ArrayList<>();
        for (Path root : searchRoots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".xtb"))
                        .forEach(p -> {
                            File file = p.toFile();
                            if (!found.contains(file)) {
                                found.add(file);
                            }
                        });
            } catch (IOException | UncheckedIOException e) {
                log.warn("Failed to scan for XTB files in " + root + ": " + e.getMessage());
            }
        }
        return found;
    }

    public static List<File> findAllXtbByLocale(String locale, File baseDir,
                                                 List<Path> additionalSearchPaths, BuildLog log) {
        String normalizedLocale = locale.replace("-", "_");
        java.util.regex.Pattern localePattern = java.util.regex.Pattern.compile(
                "[_\\-.](" + java.util.regex.Pattern.quote(locale)
                        + "|" + java.util.regex.Pattern.quote(normalizedLocale)
                        + ")[_\\-.]");

        List<Path> searchRoots = buildSearchRoots(baseDir, additionalSearchPaths);
        List<File> found = new ArrayList<>();
        for (Path root : searchRoots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".xtb"))
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return localePattern.matcher(name).find();
                        })
                        .forEach(p -> {
                            File file = p.toFile();
                            if (!found.contains(file)) {
                                found.add(file);
                            }
                        });
            } catch (IOException | UncheckedIOException e) {
                log.warn("Failed to scan for XTB files in " + root + ": " + e.getMessage());
            }
        }

        if (found.isEmpty()) {
            log.warn("No XTB file found for locale '" + locale + "' in " + baseDir);
        }
        return found;
    }

    @Deprecated
    public static File findXtbByLocale(String locale, File baseDir, BuildLog log) {
        List<File> files = findAllXtbByLocale(locale, baseDir, Collections.emptyList(), log);
        return files.isEmpty() ? null : files.get(0);
    }

    private static List<Path> buildSearchRoots(File baseDir, List<Path> additionalSearchPaths) {
        List<Path> roots = new ArrayList<>();
        roots.add(baseDir.toPath());
        roots.add(baseDir.toPath().resolve("src/main/java"));
        roots.add(baseDir.toPath().resolve("src/main/resources"));
        if (additionalSearchPaths != null) {
            roots.addAll(additionalSearchPaths);
        }
        return roots;
    }
}
