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

public class PatternMatching {

    // Java 16: pattern matching for instanceof
    public static String describe(Object obj) {
        if (obj instanceof String s) {
            return "string:" + s.length();
        } else if (obj instanceof Integer i) {
            return "int:" + i;
        } else if (obj instanceof int[] arr) {
            return "array:" + arr.length;
        } else {
            return "other";
        }
    }

    // Java 16: pattern matching with &&
    public static boolean isLongString(Object obj) {
        return obj instanceof String s && s.length() > 5;
    }
}
