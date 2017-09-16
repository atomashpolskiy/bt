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

package bt;

import java.util.function.Function;

import static org.junit.Assert.assertNotNull;

/**
 * @since 1.0
 */
public class TestUtil {

    /**
     * Assert that the provided function fails with an exception, that contains the provided message.
     * Use some fake parameter to get around the type checker, i.e.
     *
     * assertExceptionWithMessage(it -> {throw new RuntimeException("abcde");}, "bcd") => true
     *
     * @since 1.0
     */
    public static void assertExceptionWithMessage(Function<?,?> function, String message) {
        if (message == null || message.isEmpty()) {
            throw new RuntimeException("Invalid test case: empty message (will match everything)");
        }

        Exception e = null;
        try {
            function.apply(null);
        } catch (Exception e1) {
            e = e1;
        }

        assertNotNull(e);
        if (e.getMessage() == null || !e.getMessage().contains(message)) {
            throw new RuntimeException("Expected string containing text: '" + message +
                    "', but actual was: '" + e.getMessage() + "'", e);
        }
    }

    /**
     * Creates a periodical list of numbers from 1 to 9, starting with {@code start}
     * and looping if {@code size} is greater than 9.
     *
     * @param size Length of the resulting array
     * @param start Number between 1 and 9 inclusive; sequence starts with this number
     * @return Array of numbers
     * @since 1.3
     */
    public static byte[] sequence(int size, int start) {

        if (start < 1 || start > 9) {
            throw new RuntimeException("Illegal starting number (must be 1-9): " + start);
        }

        byte[] sequence = new byte[size];

        byte b = (byte) (start - 1);
        for (int i = 0; i < size; i++) {
            if (++b == 10) {
                b = 1;
            }
            sequence[i] = b;
        }

        return sequence;
    }

    /**
     * Creates a periodical list of numbers from 1 to 9, starting with 1
     * and looping if {@code size} is greater than 9.
     *
     * @param size Length of the resulting array
     * @return Array of numbers
     * @since 1.3
     */
    public static byte[] sequence(int size) {
        return sequence(size, 1);
    }
}
