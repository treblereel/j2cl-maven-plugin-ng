package org.treblereel.j2cl.plugin.tools;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavacInternalsTest {

    @Test
    void opensJavacInternalsToPluginModule() throws ReflectiveOperationException {
        JavacInternals.openToPlugin();

        Module pluginModule = JavacInternals.class.getModule();
        Module compilerModule = ModuleLayer.boot().findModule("jdk.compiler").orElseThrow();
        assertTrue(compilerModule.isExported("com.sun.tools.javac.util", pluginModule));

        Constructor<?> constructor = Class.forName("com.sun.tools.javac.util.Context").getDeclaredConstructor();
        Object context = constructor.newInstance();
        assertTrue(context.getClass().getName().endsWith(".Context"));
    }
}
