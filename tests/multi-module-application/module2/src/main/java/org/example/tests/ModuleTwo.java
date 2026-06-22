package org.example.tests;

import org.example.ModuleOne;

public class ModuleTwo {

    public String greet() {
        return "Hello from Module Two!" + new ModuleOne().greet();
    }
}
