package org.example.config;

import org.example.xbt.TranslationsFileConfig;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface Config {

    String initialScriptFilename();

    String webappDirectory();

    String compilationLevel();

    Map<String, String> defines();

    boolean rewritePolyfills();

    TranslationsFileConfig translationsFile();

    boolean enableSourcemaps();

    Map<String, String> annotationProcessorsArgs();

    List<File> getExtraClasspath();

    File getBootstrapClasspath();

    List<File> getJsZip();
}
