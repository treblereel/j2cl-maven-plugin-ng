package org.treblereel.j2cl.plugin.tools;

import java.io.File;
import java.util.List;

public record AptPath(File annotationProcessorFile, List<String> annotationProcessorName) {}
