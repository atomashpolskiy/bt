/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

package bt.peer.lan;

import java.util.Random;

/**
 * Opaque valueStr, allowing the sending client to filter out its own announces if it receives them via multicast loopback.
 *
 * @since 1.6
 */
public class Cookie {

    /**
     * @return New cookie
     * @since 1.6
     */
    public static Cookie newCookie() {
        int value = new Random().nextInt() & 0x7fffffff; // need positive valueStr
        return new Cookie(value);
    }

    /**
     * Parse cookie from its' string representation.
     *
     * @since 1.6
     */
    public static Cookie fromString(String s) {
        return new Cookie(Integer.parseInt(s));
    }

    /**
     * Check if two cookies have the same value.
     *
     * @return true if cookies have the same value
     * @since 1.6
     */
    public static boolean sameValue(Cookie c1, Cookie c2) {
        return c1.value == c2.value;
    }

    /**
     * @return Maximum number of chars in the string representation
     * @since 1.6
     */
    public static int maxLength() {
        return 10; // max digits in positive integer
    }

    private final int value;

    private Cookie(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative valueStr: " + value);
        }
        this.value = value;
    }

    /**
     * Write itself to the provided appendable entity.
     *
     * @since 1.6
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
