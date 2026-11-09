package org.treblereel.j2cl.plugin;

public final class Versions {
    public static final String J2CL_VERSION = "${j2cl.version}";
    public static final String PLUGIN_VERSION = "${project.version}";
    public static final String CLOSURE_VERSION = "${closure.compiler.unshaded.version}";

    private Versions() {
    }
}
