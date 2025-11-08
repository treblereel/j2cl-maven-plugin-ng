package example.lib;

public class Greeter {

    private final String name;

    public Greeter(String name) {
        this.name = name;
    }

    public String hello() {
        return "Hello, " + name + "!";
    }

    public String goodbye() {
        return "Goodbye, " + name + "!";
    }
}
