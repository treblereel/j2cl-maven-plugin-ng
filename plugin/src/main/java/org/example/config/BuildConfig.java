package org.example.config;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.example.log.BuildLog;
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
    private final Map<String, String> defines;
    private final boolean rewritePolyfills;
    private final TranslationsFileConfig translationsFile;
    private final boolean enableSourcemaps;
    private final Map<String, String> annotationProcessorsArgs;

    public BuildConfig(List<File> extraClasspath, List<Artifact> extraJsZips, File bootstrapClasspath,
                       String initialScriptFilename, String webappDirectory, String compilationLevel, Map<String, String> defines,
                       boolean rewritePolyfills, TranslationsFileConfig translationsFile, boolean enableSourcemaps,
                       Map<String, String> annotationProcessorsArgs) {
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
        this.annotationProcessorsArgs = annotationProcessorsArgs;
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
    public Map<String, String> defines() {
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
}
