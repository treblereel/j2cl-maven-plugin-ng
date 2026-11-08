package example.processor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MessageProcessor extends AbstractProcessor {
    private boolean generated;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (!generated && !round.processingOver()) {
            generated = true;
            try (var writer = processingEnv.getFiler().createSourceFile("example.lib.GeneratedMessage").openWriter()) {
                writer.write("package example.lib; public class GeneratedMessage {"
                        + "public static String value() { return \"Maven APT\"; } }");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return false;
    }
}
