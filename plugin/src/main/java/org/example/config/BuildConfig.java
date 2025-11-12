package org.example.config;

import org.apache.maven.plugin.logging.Log;
import org.example.log.BuildLog;

import java.io.File;
import java.util.List;

public class BuildConfig implements Config, BuildLog {


    private final List<File> extraClasspath;
    private final Log log;
    private final File bootstrapClasspath;

    public BuildConfig(List<File> extraClasspath, File bootstrapClasspath, Log log) {
        this.extraClasspath = extraClasspath;
        this.bootstrapClasspath = bootstrapClasspath;
        this.log = log;
    }


    @Override
    public List<File> getExtraClasspath() {
        return extraClasspath;
    }

    @Override
    public File getBootstrapClasspath() {
        return bootstrapClasspath;
    }

    @Override
    public void debug(String msg) {
        log.debug(msg);
    }

    @Override
    public void info(String msg) {
        log.info(msg);
    }

    @Override
    public void warn(String msg) {
        log.warn(msg);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log.warn(msg, t);
    }

    @Override
    public void warn(Throwable t) {
        log.warn(t);
    }

    @Override
    public void error(String msg) {
        log.error(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
        log.error(msg, t);
    }

    @Override
    public void error(Throwable t) {
        log.error(t);
    }
}
