package example.wasm.app;

import example.wasm.lib1.MathUtils;
import example.wasm.lib2.StringUtils;

public class Calculator {

    public static int compute(int a, int b) {
        return MathUtils.add(a, MathUtils.multiply(a, b));
    }

    public static String describe(int a, int b) {
        return StringUtils.describeSum(a, b);
    }

    public static int factorialSum(int a, int b) {
        return MathUtils.add(MathUtils.factorial(a), MathUtils.factorial(b));
    }
}
