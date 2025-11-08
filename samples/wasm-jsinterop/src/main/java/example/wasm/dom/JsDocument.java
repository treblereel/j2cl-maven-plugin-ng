package example.wasm.dom;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(isNative = true, name = "Document", namespace = JsPackage.GLOBAL)
public class JsDocument {

    @JsProperty
    public native JsElement getBody();

    @JsMethod
    public native JsElement createElement(String tagName);

    @JsMethod
    public native JsElement getElementById(String id);
}
