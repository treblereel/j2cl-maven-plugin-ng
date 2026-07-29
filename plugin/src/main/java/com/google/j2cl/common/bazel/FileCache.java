package com.google.j2cl.common.bazel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

public final class FileCache<T> {

    public interface FileFunction<V> {
        V apply(Path path) throws IOException;
    }

    private final ConcurrentHashMap<String, T> cache = new ConcurrentHashMap<>();
    private final FileFunction<T> fn;

    public FileCache(FileFunction<T> fn, int cacheSize) {
        this.fn = fn;
    }

    public FileCache(FileFunction<T> fn, int cacheSize, int parallelism) {
        this.fn = fn;
    }

    public T get(String path) {
        return cache.computeIfAbsent(path, p -> {
            try {
                return fn.apply(Paths.get(p));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
