package com.google.j2cl.common.bazel;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import com.google.j2cl.common.CommandLineParser;
import com.google.j2cl.common.Problems;
import org.kohsuke.args4j.Option;

public abstract class BazelWorker {

    protected final Problems problems = new Problems();

    @Option(name = "-profileOutput", hidden = true)
    Path profileOutput = null;

    protected abstract void run();

    private int processRequest(List<String> args, PrintWriter pw, String sandboxDir) {
        CommandLineParser parser = new CommandLineParser(this, Path.of(sandboxDir));
        try {
            parser.parseArgument(args);
        } catch (org.kohsuke.args4j.CmdLineException e) {
            problems.error("%s", e.getMessage());
            return problems.reportAndGetExitCode(pw);
        }

        try {
            run();
        } catch (RuntimeException | Error e) {
            if (!Problems.Exit.isRootCause(e)) {
                throw e;
            }
        }
        return problems.reportAndGetExitCode(pw);
    }

    public static void start(String[] args, Supplier<BazelWorker> workerSupplier) throws Exception {
        throw new UnsupportedOperationException("BazelWorker.start is not supported in Maven context");
    }
}
