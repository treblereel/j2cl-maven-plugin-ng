package org.example.tools;

import java.io.File;
import java.util.List;

public record AptPath(File annotationProcessorFile, List<String> annotationProcessorName) {}
