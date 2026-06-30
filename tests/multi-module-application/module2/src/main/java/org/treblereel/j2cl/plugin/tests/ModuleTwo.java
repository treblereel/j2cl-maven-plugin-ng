package org.treblereel.j2cl.plugin.tests;

import org.treblereel.j2cl.plugin.ModuleOne;

public class ModuleTwo {

    public String greet() {
        return "Hello from Module Two!" + new ModuleOne().greet();
    }
}
