package example.wasm;

import example.wasm.dom.JsDocument;
import example.wasm.dom.JsElement;
import example.wasm.dom.JsWindow;

public class DomDemo {

    public static int createElements(int count) {
        JsDocument doc = JsWindow.getDocument();
        JsElement container = doc.getElementById("wasm-dom-container");

        for (int i = 0; i < count; i++) {
            JsElement div = doc.createElement("div");
            div.setInnerText("Element #" + (i + 1) + " created by Java/WASM");
            div.getStyle().setColor("#c084fc");
            div.getStyle().setPadding("4px 8px");
            div.getStyle().setMarginTop("4px");
            div.getStyle().setBackgroundColor("#1e293b");
            div.getStyle().setBorder("1px solid #475569");
            div.getStyle().setBorderRadius("4px");
            div.getStyle().setFontSize("0.85rem");
            container.appendChild(div);
        }
        return count;
    }

    public static int updateElement(int value) {
        JsDocument doc = JsWindow.getDocument();
        JsElement el = doc.getElementById("wasm-dom-value");
        el.setInnerText("WASM says: " + value + " (updated via DOM)");
        el.getStyle().setColor(value > 50 ? "#10b981" : "#fbbf24");
        return value;
    }

    public static int clearContainer() {
        JsDocument doc = JsWindow.getDocument();
        JsElement container = doc.getElementById("wasm-dom-container");
        container.setInnerHTML("");
        return 0;
    }
}
