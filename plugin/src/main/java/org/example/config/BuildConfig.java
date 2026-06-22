package org.example.config;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.example.log.BuildLog;
import org.example.tools.AptPath;
import org.example.xbt.TranslationsFileConfig;

import java.io.File;
import java.util.List;
import java.util.Map;

public class BuildConfig implements Config {


    private final List<File> extraClasspath;
    private final File bootstrapClasspath;
    private final List<Artifact> extraJsZips;
    private final String initialScriptFilename;
    private final String webappDirectory;
    private final String compilationLevel;
    private final Map<String, Object> defines;
    private final boolean rewritePolyfills;
    private final TranslationsFileConfig translationsFile;
    private final boolean enableSourcemaps;
    private final String languageOut;
    private final boolean checkAssertions;
    private final String env;
    private final Map<String, String> annotationProcessorsArgs;
    private final List<AptPath> extraAnnotationProcessors;

    public BuildConfig(List<File> extraClasspath, List<Artifact> extraJsZips, File bootstrapClasspath,
                       String initialScriptFilename, String webappDirectory, String compilationLevel, Map<String, Object> defines,
                       boolean rewritePolyfills, TranslationsFileConfig translationsFile, boolean enableSourcemaps,
                       String languageOut, boolean checkAssertions, String env,
                       Map<String, String> annotationProcessorsArgs) {
        this(extraClasspath, extraJsZips, bootstrapClasspath, initialScriptFilename, webappDirectory,
                compilationLevel, defines, rewritePolyfills, translationsFile, enableSourcemaps,
                languageOut, checkAssertions, env,
                annotationProcessorsArgs, List.of());
    }

    public BuildConfig(List<File> extraClasspath, List<Artifact> extraJsZips, File bootstrapClasspath,
                       String initialScriptFilename, String webappDirectory, String compilationLevel, Map<String, Object> defines,
                       boolean rewritePolyfills, TranslationsFileConfig translationsFile, boolean enableSourcemaps,
                       String languageOut, boolean checkAssertions, String env,
                       Map<String, String> annotationProcessorsArgs, List<AptPath> extraAnnotationProcessors) {
        this.extraClasspath = extraClasspath;
        this.extraJsZips = extraJsZips;
        this.bootstrapClasspath = bootstrapClasspath;
        this.initialScriptFilename = initialScriptFilename;
        this.webappDirectory = webappDirectory;
        this.compilationLevel = compilationLevel;
        this.defines = defines;
        this.rewritePolyfills = rewritePolyfills;
        this.translationsFile = translationsFile;
        this.enableSourcemaps = enableSourcemaps;
        this.languageOut = languageOut;
        this.checkAssertions = checkAssertions;
        this.env = env;
        this.annotationProcessorsArgs = annotationProcessorsArgs;
        this.extraAnnotationProcessors = extraAnnotationProcessors;
    }

    @Override
    public String initialScriptFilename() {
        return initialScriptFilename;
    }

    @Override
    public String webappDirectory() {
        return webappDirectory;
    }

    @Override
    public String compilationLevel() {
        return compilationLevel;
    }

    @Override
    public Map<String, Object> defines() {
        return defines;
    }

    @Override
    public boolean rewritePolyfills() {
        return rewritePolyfills;
    }

    @Override
    public TranslationsFileConfig translationsFile() {
        return translationsFile;
    }

    @Override
    public boolean enableSourcemaps() {
        return enableSourcemaps;
    }

    @Override
    public String languageOut() {
        return languageOut;
    }

    @Override
    public boolean checkAssertions() {
        return checkAssertions;
    }

    @Override
    public String env() {
        return env;
    }

    @Override
    public Map<String, String> annotationProcessorsArgs() {
        return annotationProcessorsArgs;
    }

    @Override
    public List<File> getExtraClasspath() {
        return extraClasspath;
    }

    @Override
    public List<File> getJsZip() {
        return extraJsZips.stream().map(Artifact::getFile).toList();
    }

    @Override
    public File getBootstrapClasspath() {
        return bootstrapClasspath;
    }

    @Override
    public List<AptPath> getExtraAnnotationProcessors() {
        return extraAnnotationProcessors;
    }
}
