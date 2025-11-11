package org.example.config;

import java.io.File;
import java.util.List;

public class BuildConfig implements Config {


  private final List<File> extraClasspath;

  public BuildConfig(List<java.io.File> extraClasspath) {
    this.extraClasspath = extraClasspath;
  }


  @Override
  public List<java.io.File> getExtraClasspath() {
    return extraClasspath;
  }
}
