/*
 * Copyright © 2026 j2cl-maven-plugin authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package j2clsample.java17;

public class SwitchExpressions {

    // Java 14: switch expression with arrow syntax
    public static String dayType(int day) {
        return switch (day) {
            case 1, 7 -> "weekend";
            case 2, 3, 4, 5, 6 -> "weekday";
            default -> "unknown";
        };
    }

    // Java 14: switch expression with yield
    public static int score(String grade) {
        return switch (grade) {
            case "A" -> 100;
            case "B" -> 80;
            case "C" -> {
                int base = 50;
                yield base + 10;
            }
            default -> 0;
        };
    }
}
