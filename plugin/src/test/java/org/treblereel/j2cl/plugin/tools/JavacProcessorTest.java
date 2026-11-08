package org.treblereel.j2cl.plugin.tools;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.treblereel.j2cl.plugin.log.BuildLog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavacProcessorTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runsOnlyExplicitlySelectedProcessors(boolean explicit) throws Exception {
        Path source = Files.writeString(directory.resolve("App.java"), "class App {}");
        Path services = directory.resolve("META-INF/services/javax.annotation.processing.Processor");
        Files.createDirectories(services.getParent());
        Files.writeString(services, Generator.class.getName());
        Path generated = Files.createDirectory(directory.resolve("generated"));
        Path classes = Files.createDirectory(directory.resolve("classes"));
        Path processorClasses = Path.of(Generator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        BuildLog log = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
                new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
        Javac javac = new Javac(log, generated.toFile(), List.of(directory.toFile()),
                List.of(directory.toFile(), processorClasses.toFile()), classes.toFile(), null,
                explicit ? List.of(Generator.class.getName()) : List.of(), List.of());
        assertTrue(javac.compile(List.of(source.toString())));
        assertEquals(explicit, Files.exists(generated.resolve("Generated.java")));
    }

    public static class Generator extends AbstractProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (!round.processingOver() && processingEnv.getElementUtils().getTypeElement("Generated") == null) {
                try (var writer = processingEnv.getFiler().createSourceFile("Generated").openWriter()) {
                    writer.write("class Generated {}");
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return false;
        }
    }
}
