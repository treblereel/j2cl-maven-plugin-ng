package org.example.task;

public enum OutputTypes {

    UNZIPPED_DEPENDENCIES("unzipped"),

    /**
     * A special output type to indicate to use project's own sources, or
     * the contents of an external dependency's jar/zip file.
     */
    INPUT_SOURCES("input-sources"),
    /**
     * Represents the contents of a project if it were built into a jar
     * file as an external dependency. Ostensibly should contain the
     * un-stripped bytecode for a project and all of its resources (so
     * that downstream projects can look for those resources on the
     * classpath), but also presently ends up holding generated resources
     * (so that the {@link #GENERATED_SOURCES} task can copy them out),
     * and at that point it might as well contain the original Java
     * sources too, unstripped. Including those sources however means
     * that this becomes the source of truth for stripping sources,
     * rather than the union of {@link #INPUT_SOURCES} and {@link #GENERATED_SOURCES}.
     * This conflict arises since there could be .js files in the original
     * sources, and we must copy them here since downstream projects
     * could require them on the classpath - and after APT runs, we can't
     * tell which sources were copied in and which came from sources,
     * so we can't let downstream closure point to this (or generated
     * sources) and input sources, it will find duplicate files.
     */
    BYTECODE("bytecode"),
    /**
     * Sources where the Java code has had GwtIncompatible members stripped.
     */
    STRIPPED_SOURCES("stripped_sources"),
    /**
     * Java bytecode with GwtIncompatible members have been stripped.
     */
    STRIPPED_BYTECODE("stripped_bytecode"),
    /**
     * Simplified Java bytecode with GwtIncomatible members removed.
     */
    STRIPPED_BYTECODE_HEADERS("stripped_bytecode_headers"),
    /**
     * J2CL output, and other JS sources.
     */
    TRANSPILED_JS("transpiled_js"),
    /**
     * Single JS file with all sources, unpruned, from a project
     */
    BUNDLED_JS("bundled_js"),
    /**
     * Runnable app including all bundled_js files from a project's runtime classpath
     */
    BUNDLED_JS_APP("bundled_js_app"),
    /**
     * Optimized build including all js from a project's runtime classpath
     */
    OPTIMIZED_JS("optimized_js"),

    FINAL_TASK("final_task");

    private final String name;

    OutputTypes(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
