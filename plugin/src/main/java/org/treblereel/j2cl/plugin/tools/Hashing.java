package org.treblereel.j2cl.plugin.tools;

import com.google.common.hash.Hasher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class Hashing {

  public static String hash(List<Path> paths) {
    Hasher hasher = com.google.common.hash.Hashing.murmur3_128().newHasher();
    for (Path path : paths) {
      try (Stream<Path> stream = Files.walk(path)) {
        stream
                .filter(Files::isRegularFile)
                .map(path::relativize)
                .sorted(Comparator.comparing(Path::toString))
                .forEach(rel -> {
                  try {
                    hasher.putString(rel.toString().replace('\\', '/'), StandardCharsets.UTF_8);
                    hasher.putByte((byte) 0);
                    hasher.putBytes(Files.readAllBytes(path.resolve(rel)));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return hasher.hash().toString();
  }
}