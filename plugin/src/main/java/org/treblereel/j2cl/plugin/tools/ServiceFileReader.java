package org.treblereel.j2cl.plugin.tools;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Collectors;

public final class ServiceFileReader {

  private static final String SERVICE_PATH =
          "META-INF/services/javax.annotation.processing.Processor";

  public static List<String> readProcessors(Path jar) throws IOException {
    try (ZipFile zf = new ZipFile(jar.toFile())) {
      ZipEntry e = zf.getEntry(SERVICE_PATH);
      if (e == null) {
        return List.of();
      }
      try (InputStream in = zf.getInputStream(e);
           BufferedReader br = new BufferedReader(
                   new InputStreamReader(in, StandardCharsets.UTF_8))) {

        return br.lines()
                .map(ServiceFileReader::stripBOM)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .collect(Collectors.toList());
      }
    }
  }

  static String stripBOM(String s) {
    if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
      return s.substring(1);
    }
    return s;
  }
}