package org.treblereel.j2cl.plugin.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import sun.misc.Unsafe;

/**
 * Experimental bootstrap that opens javac implementation packages to the Maven plugin.
 *
 * <p>This deliberately mirrors the technique used by Lombok. It is not a supported JDK API and may stop working
 * on any JDK update. Keep all of the workaround isolated here so it can be replaced by a forked compiler process.
 */
public final class JavacInternals {

    private static final List<String> PACKAGES = List.of(
            "com.sun.tools.javac.api",
            "com.sun.tools.javac.code",
            "com.sun.tools.javac.comp",
            "com.sun.tools.javac.file",
            "com.sun.tools.javac.jvm",
            "com.sun.tools.javac.main",
            "com.sun.tools.javac.model",
            "com.sun.tools.javac.parser",
            "com.sun.tools.javac.processing",
            "com.sun.tools.javac.tree",
            "com.sun.tools.javac.util"
    );

    private static volatile boolean initialized;

    private JavacInternals() {
    }

    /** Opens all javac implementation packages to the module containing this plugin. */
    public static synchronized void openToPlugin() {
        if (initialized) {
            return;
        }

        Module compilerModule = ModuleLayer.boot()
                .findModule("jdk.compiler")
                .orElseThrow(() -> new IllegalStateException("The jdk.compiler module is not available"));
        Module pluginModule = JavacInternals.class.getModule();

        // Explicit --add-exports is sufficient for J2CL. Do not invoke Unsafe when the hosting JVM has already
        // granted access, which also avoids the terminal-deprecation warning on JDK 24 and newer.
        if (hasAccess(compilerModule, pluginModule)) {
            initialized = true;
            return;
        }

        try {
            Unsafe unsafe = getUnsafe();
            Method implAddOpens = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);

            // AccessibleObject hides its override field on modern JDKs. Its first field has the same offset as
            // the marker below; setting it is the core of Lombok's module-opening workaround.
            long accessibleOverrideOffset = unsafe.objectFieldOffset(FirstField.class.getDeclaredField("first"));
            unsafe.putBooleanVolatile(implAddOpens, accessibleOverrideOffset, true);

            for (String packageName : PACKAGES) {
                implAddOpens.invoke(compilerModule, packageName, pluginModule);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to open javac internals to the J2CL Maven plugin", e);
        }

        List<String> inaccessiblePackages = PACKAGES.stream()
                .filter(packageName -> !compilerModule.isExported(packageName, pluginModule))
                .toList();
        if (!inaccessiblePackages.isEmpty()) {
            throw new IllegalStateException("Unable to open javac packages: " + inaccessiblePackages);
        }
        initialized = true;
    }

    private static boolean hasAccess(Module compilerModule, Module pluginModule) {
        return PACKAGES.stream().allMatch(packageName -> compilerModule.isExported(packageName, pluginModule));
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    @SuppressWarnings("unused")
    private static final class FirstField {
        boolean first;
    }
}
