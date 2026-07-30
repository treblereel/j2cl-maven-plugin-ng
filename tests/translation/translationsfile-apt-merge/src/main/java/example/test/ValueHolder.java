package example.test;

public class ValueHolder {

    public static String getExpectedManualValue() {
        return System.getProperty("holder.value");
    }

    public static String getExpectedAptValue() {
        return System.getProperty("holder.aptValue");
    }
}
