package org.example.config;

import java.io.File;
import java.util.List;

public class BuildConfig implements Config {


  private final List<File> extraClasspath;

  public BuildConfig(List<File> extraClasspath) {
    this.extraClasspath = extraClasspath;
  }


  @Override
  public List<File> getExtraClasspath() {
    return extraClasspath;
  }

  @Override
  public File getBootstrapClasspath() {
    return null;
  }
}
