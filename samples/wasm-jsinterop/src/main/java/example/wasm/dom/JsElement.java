package example.wasm.dom;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(isNative = true, name = "Element", namespace = JsPackage.GLOBAL)
public class JsElement {

    @JsProperty
    public native String getInnerText();

    @JsProperty
    public native void setInnerText(String text);

    @JsProperty
    public native String getInnerHTML();

    @JsProperty
    public native void setInnerHTML(String html);

    @JsMethod
    public native void setAttribute(String name, String value);

    @JsMethod
    public native void appendChild(JsElement child);

    @JsProperty
    public native JsStyle getStyle();
}
