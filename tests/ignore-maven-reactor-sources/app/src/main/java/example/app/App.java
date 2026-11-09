package example.app;

import example.lib.GeneratedMessage;
import example.lib.Library;
import jsinterop.annotations.JsType;

@JsType
public class App {
    public static String message() {
        return Library.message() + GeneratedMessage.value();
    }
}
