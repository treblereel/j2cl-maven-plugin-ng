package example.wasm.dom;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(isNative = true, name = "Window", namespace = JsPackage.GLOBAL)
public class JsWindow {

    @JsProperty(namespace = JsPackage.GLOBAL, name = "document")
    public static native JsDocument getDocument();

    @JsMethod(namespace = JsPackage.GLOBAL, name = "alert")
    public static native void alert(String message);
}
