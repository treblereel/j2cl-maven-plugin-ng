package example.app;

import elemental2.dom.DomGlobal;
import example.lib.Greeter;
import org.treblereel.j2cl.processors.annotations.GWT3EntryPoint;

/** Multi-module sample entry point. */
public final class Main {

    /** Application bootstrap. */
    @GWT3EntryPoint
    public void onLoad() {
        Greeter greeter = new Greeter("J2CL");
        DomGlobal.document.body.textContent = greeter.hello();
        DomGlobal.console.log(greeter.goodbye());
    }
}
