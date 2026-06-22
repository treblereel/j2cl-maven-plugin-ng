package org.example.config;

import org.example.xbt.TranslationsFileConfig;

import org.example.tools.AptPath;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface Config {

    String initialScriptFilename();

    String webappDirectory();

    String compilationLevel();

    Map<String, Object> defines();

    boolean rewritePolyfills();

    TranslationsFileConfig translationsFile();

    boolean enableSourcemaps();

    String languageOut();

    boolean checkAssertions();

    String env();

    Map<String, String> annotationProcessorsArgs();

    List<File> getExtraClasspath();

    File getBootstrapClasspath();

    List<File> getJsZip();

    default List<AptPath> getExtraAnnotationProcessors() {
        return List.of();
    }
}
