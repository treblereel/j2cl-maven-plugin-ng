package example.wasm;

import java.util.Arrays;

public class Benchmark {

    public static int sortArray(int size) {
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = size - i;
        }
        Arrays.sort(arr);
        return arr[0];
    }

    public static int computePrimes(int limit) {
        boolean[] sieve = new boolean[limit + 1];
        Arrays.fill(sieve, true);
        sieve[0] = sieve[1] = false;
        for (int i = 2; i * i <= limit; i++) {
            if (sieve[i]) {
                for (int j = i * i; j <= limit; j += i) {
                    sieve[j] = false;
                }
            }
        }
        int count = 0;
        for (boolean b : sieve) {
            if (b) count++;
        }
        return count;
    }
}
