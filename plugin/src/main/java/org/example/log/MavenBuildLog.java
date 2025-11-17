package org.example.log;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;

public class MavenBuildLog implements BuildLog {


    private final Log logger;

    public MavenBuildLog(AbstractMojo mojo) {
        this.logger = mojo.getLog();
    }

    @Override
    public void debug(String msg) {
        this.logger.debug(msg);
    }

    @Override
    public void info(String msg) {
        this.logger.info(msg);
    }

    @Override
    public void warn(String msg) {
        this.logger.warn(msg);
    }

    @Override
    public void warn(String msg, Throwable t) {
        this.logger.warn(msg, t);
    }

    @Override
    public void warn(Throwable t) {
        this.logger.warn(t);
    }

    @Override
    public void error(String msg) {
        this.logger.error(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
        this.logger.error(msg, t);
    }

    @Override
    public void error(Throwable t) {
        this.logger.error(t);
    }
}
