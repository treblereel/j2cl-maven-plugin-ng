package org.treblereel.j2cl.plugin.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurbineRunnerTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releasesClasspathAndProcessorJarsOnSuccessAndFailure(boolean failure) throws Exception {
        Path work = Files.createDirectory(directory.resolve("compiler with spaces"));
        Path source = Files.writeString(work.resolve("App.java"), failure ? "class App { Missing value; }" : "class App {}");
        Path dependency = work.resolve("dependency.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(dependency))) {
            zip.putNextEntry(new ZipEntry("input.txt"));
            zip.write("input".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path processor = work.resolve("processor.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(processor))) {
            for (Class<?> type : List.of(Processor.class, TurbineRunnerTest.class)) {
                String name = type.getName().replace('.', '/') + ".class";
                zip.putNextEntry(new ZipEntry(name));
                try (var input = type.getClassLoader().getResourceAsStream(name)) {
                    zip.write(java.util.Objects.requireNonNull(input).readAllBytes());
                }
                zip.closeEntry();
            }
        }
        Path output = work.resolve("output.jar");
        String option = "spaces and \"quotes\" and \\slashes\nnext\tline\rend";
        List<String> arguments = List.of("--output", output.toString(), "--resource_output", work.toString(), "--gensrc_output", work.toString(),
                "--sources", source.toString(), "--classpath", dependency.toString(),
                "--processorpath", processor.toString(), dependency.toString(),
                "--processors", Processor.class.getName(), "--javacopts", "--release", "21", "-Avalue=" + option, "--");
        if (failure) {
            assertThrows(IOException.class, () -> TurbineRunner.compile(arguments));
        } else {
            TurbineRunner.compile(arguments);
            assertEquals(option + "|input", Files.readString(work.resolve("options.txt")));
            try (ZipFile zip = new ZipFile(output.toFile())) {
                assertNotNull(zip.getEntry("App.class"));
            }
            Files.delete(output);
        }
        Files.writeString(dependency, "replaced");
        Files.writeString(processor, "replaced");
        Files.delete(dependency);
        Files.delete(processor);
    }

    public static class Processor extends AbstractProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() { return Set.of("*"); }
        @Override
        public Set<String> getSupportedOptions() { return Set.of("value"); }
        @Override
        public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (round.processingOver()) return false;
            try (var input = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", "input.txt").openReader(true);
                 var output = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", "options.txt").openWriter()) {
                java.io.StringWriter text = new java.io.StringWriter();
                input.transferTo(text);
                output.write(processingEnv.getOptions().get("value") + "|" + text);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return false;
        }
    }
}
