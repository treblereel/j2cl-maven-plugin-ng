package example.test;

import org.treblereel.j2cl.processors.annotations.TranslationBundle;
import org.treblereel.j2cl.processors.annotations.TranslationKey;

@TranslationBundle
public interface Messages {

    @TranslationKey(defaultValue = "Hello World")
    String helloWorld();
}
