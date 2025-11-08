package example.wasm;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public class JsBridge {

    @JsMethod(namespace = JsPackage.GLOBAL, name = "wasmLog")
    private static native void wasmLog(int code, int value);

    @JsMethod(namespace = JsPackage.GLOBAL, name = "wasmSetElement")
    private static native void wasmSetElement(int elementId, int value);

    @JsMethod(namespace = JsPackage.GLOBAL, name = "wasmNotify")
    private static native void wasmNotify(int eventType, int a, int b, int result);

    public static int addWithLog(int a, int b) {
        int result = a + b;
        wasmLog(1, result);
        wasmNotify(0, a, b, result);
        wasmSetElement(0, result);
        return result;
    }

    public static int fibonacciWithSteps(int n) {
        if (n <= 0) {
            wasmSetElement(1, 0);
            return 0;
        }
        if (n == 1) {
            wasmSetElement(1, 1);
            return 1;
        }
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int tmp = a + b;
            a = b;
            b = tmp;
            wasmLog(i, b);
        }
        wasmSetElement(1, b);
        wasmNotify(1, n, 0, b);
        return b;
    }

    public static int countPrimesWithProgress(int limit) {
        int count = 0;
        int milestone = limit / 10;
        if (milestone < 1) milestone = 1;

        for (int i = 2; i <= limit; i++) {
            if (isPrime(i)) {
                count++;
            }
            if (i % milestone == 0) {
                wasmLog(i, count);
            }
        }
        wasmSetElement(2, count);
        wasmNotify(2, limit, 0, count);
        return count;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        if (n < 4) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }
}
