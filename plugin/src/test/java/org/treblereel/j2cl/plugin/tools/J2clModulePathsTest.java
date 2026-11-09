package org.treblereel.j2cl.plugin.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class J2clModulePathsTest {
    @ParameterizedTest
    @CsvSource({
            "java/lang/NoSuchMethodException.java,java.base",
            "java/sql/Date.java,java.sql",
            "javax/sql/DataSource.java,java.sql",
            "java/util/logging/Logger.java,java.logging",
            "javax/annotation/processing/Processor.java,java.compiler",
            "javax/lang/model/element/Element.java,java.compiler",
            "javax/tools/JavaCompiler.java,java.compiler",
            "example/Hello.java,",
            "javafx/Hello.java,"})
    void classifiesSuperSourcesWithEitherPathSeparator(String path, String module) {
        assertEquals(module, J2cl.getModuleForEntry(path));
        assertEquals(module, J2cl.getModuleForEntry(path.replace('/', '\\')));
    }
}
