package j2clsample.java17;

public class SealedClasses {

    public sealed interface Shape permits Circle, Rectangle {
        double area();
    }

    public record Circle(double radius) implements Shape {
        @Override
        public double area() {
            return Math.PI * radius * radius;
        }
    }

    public record Rectangle(double width, double height) implements Shape {
        @Override
        public double area() {
            return width * height;
        }
    }

    public static String describeShape(Shape shape) {
        if (shape instanceof Circle c) {
            return "circle:r=" + c.radius();
        } else if (shape instanceof Rectangle r) {
            return "rect:" + r.width() + "x" + r.height();
        }
        return "unknown";
    }
}
