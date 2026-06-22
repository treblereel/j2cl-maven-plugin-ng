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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class VarFeatures {

    // Java 10: var for local variables
    public static String localVar() {
        var list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        var result = String.join(",", list);
        return result;
    }

    // Java 11: var in lambda parameters
    public static int lambdaVar() {
        BiFunction<Integer, Integer, Integer> sum = (var x, var y) -> x + y;
        return sum.apply(3, 4);
    }
}
