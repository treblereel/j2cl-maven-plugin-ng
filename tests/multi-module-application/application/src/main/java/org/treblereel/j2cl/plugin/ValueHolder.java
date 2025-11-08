package org.treblereel.j2cl.plugin;

public class ValueHolder {

    public static String getExpectedValue() {
        return System.getProperty("holder.value");
    }
}
