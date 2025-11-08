package example.app;

import jsinterop.annotations.JsMethod;

public final class Messages {

    private Messages() {
    }

    /** @return localized hello world message. */
    @JsMethod
    public static native String helloWorld();

    /** @return localized click me label. */
    @JsMethod
    public static native String clickMe();

    /**
     * @param name the name to greet.
     * @return localized greeting.
     */
    @JsMethod
    public static native String greeting(String name);
}
