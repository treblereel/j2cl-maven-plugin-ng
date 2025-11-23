package org.example;

import elemental2.dom.DomGlobal;

import java.util.Locale;

public class Main {

  @org.treblereel.j2cl.processors.annotations.GWT3EntryPoint
  public void entryPoint() {
    DomGlobal.console.log("Hello, J2CL!  " + ValueHolder.getExpectedValue());
  }
}