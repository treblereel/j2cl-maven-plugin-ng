package j2clsample.java17;

public class RecordFeatures {

    public record Point(int x, int y) {
        public int distanceFromOriginSquared() {
            return x * x + y * y;
        }
    }

    public interface Named {
        String name();
    }

    public record Person(String name, int age) implements Named {
    }

    public record PositiveValue(int value) {
        public PositiveValue {
            if (value < 0) {
                value = 0;
            }
        }
    }
}
