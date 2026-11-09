package org.treblereel.j2cl.plugin.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMapInput;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;

/** Keeps ZIP source maps independent of native path separators and Closure's ZIP cache. */
public final class ClosureCompiler extends Compiler {
    public ClosureCompiler() {
    }

    public ClosureCompiler(PrintStream output) {
        super(output);
    }

    @Override
    public void addInputSourceMap(String sourceFileName, SourceMapInput input) {
        String name = input.getOriginalPath().replace('\\', '/');
        int separator = name.indexOf("!/");
        if (separator >= 0 && name.substring(0, separator).endsWith(".zip")
                && !name.equals(sourceFileName.replace('\\', '/') + ".inline.map")) {
            try (ZipFile zip = new ZipFile(name.substring(0, separator))) {
                ZipEntry entry = zip.getEntry(name.substring(separator + 2));
                if (entry == null) throw new IOException("Source map entry not found: " + name);
                try (var stream = zip.getInputStream(entry)) {
                    input = new SourceMapInput(SourceFile.fromCode(name,
                            new String(stream.readAllBytes(), StandardCharsets.UTF_8), SourceKind.NON_CODE));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read source map " + name, e);
            }
        }
        super.addInputSourceMap(sourceFileName, input);
    }
}
