package example.wasm.dom;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(isNative = true, name = "CSSStyleDeclaration", namespace = JsPackage.GLOBAL)
public class JsStyle {

    @JsProperty
    public native void setColor(String color);

    @JsProperty
    public native void setBackgroundColor(String color);

    @JsProperty
    public native void setFontSize(String size);

    @JsProperty
    public native void setPadding(String padding);

    @JsProperty
    public native void setBorder(String border);

    @JsProperty
    public native void setBorderRadius(String radius);

    @JsProperty
    public native void setMarginTop(String margin);
}
