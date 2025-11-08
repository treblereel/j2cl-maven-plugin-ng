package example.app;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import jsinterop.annotations.JsType;

@JsType
public final class App {

    private App() {
    }

    /**
     * @param name the name to greet.
     * @return localized greeting.
     */
    public static String greet(final String name) {
        return Messages.greeting(name);
    }

    /** Application bootstrap. */
    public static void onModuleLoad() {
        HTMLButtonElement btn = (HTMLButtonElement)
            DomGlobal.document.getElementById("btn");
        HTMLDivElement greeting = (HTMLDivElement)
            DomGlobal.document.getElementById("greeting");

        btn.textContent = Messages.clickMe();
        btn.addEventListener("click", e -> {
            greeting.textContent =
                Messages.helloWorld() + " " + greet("J2CL");
        });
    }
}
