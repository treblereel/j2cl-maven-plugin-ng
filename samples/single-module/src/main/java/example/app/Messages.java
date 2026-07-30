package example.app;

import jsinterop.annotations.JsMethod;

public class Messages {

    @JsMethod
    public static native String helloWorld();

    @JsMethod
    public static native String clickMe();

    @JsMethod
    public static native String greeting(String name);
}
