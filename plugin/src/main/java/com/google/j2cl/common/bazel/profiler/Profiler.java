package com.google.j2cl.common.bazel.profiler;

import java.nio.file.Path;

public interface Profiler {

    static Profiler create(Path workdir, Path profileOutput) {
        return () -> {};
    }

    void stopProfile();
}
