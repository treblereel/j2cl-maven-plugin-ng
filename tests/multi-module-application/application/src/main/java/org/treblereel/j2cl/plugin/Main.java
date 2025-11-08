package org.treblereel.j2cl.plugin;

import elemental2.dom.DomGlobal;
import org.treblereel.j2cl.plugin.tests.ModuleTwo;

public class Main {

    @org.treblereel.j2cl.processors.annotations.GWT3EntryPoint
    public void entryPoint() {
        //String.format("Hello, %s!", "J2CL");
        DomGlobal.console.log("Hello, J2CL! " + ValueHolder.getExpectedValue());
        DomGlobal.console.log("Hello, From modules  " + new ModuleTwo().greet());
    }
}