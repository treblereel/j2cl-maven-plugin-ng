package example.wasm.lib2;

import example.wasm.lib1.MathUtils;

public class StringUtils {

    public static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    public static String describeSum(int a, int b) {
        int sum = MathUtils.add(a, b);
        return a + " + " + b + " = " + sum;
    }
}
