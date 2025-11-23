package org.example;

public class ValueHolder {

    public static String getExpectedValue() {
        return System.getProperty("holder.value");
    }
}
