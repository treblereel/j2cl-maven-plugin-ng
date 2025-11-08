package example.wasm;

public class StringProcessor {

    public static String reverse(String input) {
        if (input == null) return null;
        return new StringBuilder(input).reverse().toString();
    }

    public static String toUpperCase(String input) {
        if (input == null) return null;
        return input.toUpperCase();
    }

    public static int countWords(String input) {
        if (input == null || input.isEmpty()) return 0;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return 0;
        int count = 1;
        boolean inSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == ' ') {
                if (!inSpace) {
                    count++;
                    inSpace = true;
                }
            } else {
                inSpace = false;
            }
        }
        return count;
    }

    public static boolean isPalindrome(String input) {
        if (input == null) return false;
        String cleaned = input.toLowerCase().replaceAll("[^a-z0-9]", "");
        int left = 0, right = cleaned.length() - 1;
        while (left < right) {
            if (cleaned.charAt(left) != cleaned.charAt(right)) return false;
            left++;
            right--;
        }
        return true;
    }
}
