package org.treblereel.j2cl.plugin.config;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.treblereel.j2cl.plugin.tools.AptPath;
import org.treblereel.j2cl.plugin.xbt.TranslationsFileConfig;

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
