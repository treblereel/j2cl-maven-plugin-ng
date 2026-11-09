package org.treblereel.j2cl.plugin.tools;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.treblereel.j2cl.plugin.log.BuildLog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmEntryPointsTest {
    @ParameterizedTest
    @ValueSource(strings = {"example.Calculator#*", "example.Calculator#.*", "example.*#*", "example..*#.*"})
    void exportsAllMatchingMethodsAndExcludesOtherClasses(String pattern) {
        WasmBundler bundler = new WasmBundler(LOG, Map.of(), List.of(pattern));
        String exports = bundler.generateWasmExports(List.of(
                "(func $m_add__int_int__int@example.Calculator (result i32))\n"
                        + "(func $m_multiply__int_int__int@example.Calculator (result i32))\n"
                        + "(func $m_hidden__int@other.Calculator (result i32))"));
        assertTrue(exports.contains("(export \"add\""));
        assertTrue(exports.contains("(export \"multiply\""));
        assertFalse(exports.contains("hidden"));
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
