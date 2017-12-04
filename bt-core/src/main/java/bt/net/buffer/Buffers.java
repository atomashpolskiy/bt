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

package bt.net.buffer;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @since 1.2
 */
public class Buffers {

    /**
     * Searches for the first pattern match in the provided buffer.
     * Buffer's position might change in the following ways after this methods returns:
     * - if a pattern was not found, then the buffer's position will not change
     * - if a pattern was found, then the buffer's position will be right after the last index
     *   of the matching subrange (probably equal to buffer's limit)
     *
     * @return true if pattern was found in the provided buffer
     * @since 1.2
     */
    public static boolean searchPattern(ByteBuffer buf, byte[] pattern) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("Empty pattern");
        } else if (buf.remaining() < pattern.length) {
            return false;
        }

        int pos = buf.position();

        int len = pattern.length;
        int p = 31;
        int mult0 = 1;
        for (int i = 0; i < len - 1; i++) {
            mult0 *= p;
        }
        int hash = 0;
        for (int i = 0; i < len - 1; i++) {
            hash += pattern[i];
            hash *= p;
        }
        hash += pattern[len - 1];

        int bufhash = 0;
        byte[] bytes = new byte[len];
        byte b;
        for (int i = 0; i < len - 1; i++) {
            b = buf.get();
            bytes[i] = b;
            bufhash += b;
            bufhash *= p;
        }
        b = buf.get();
        bytes[bytes.length - 1] = b;
        bufhash += b;

        boolean found = false;
        do {
            if (bufhash == hash && Arrays.equals(pattern, bytes)) {
                found = true;
                break;
            } else if (!buf.hasRemaining()) {
                break;
            }
            byte next = buf.get();
            bufhash -= (bytes[0] * mult0);
            bufhash *= p;
            bufhash += next;
            System.arraycopy(bytes, 1, bytes, 0, len - 1);
            bytes[len - 1] = next;
        } while (true);

        if (!found) {
            buf.position(pos);
        }
        return found;
    }
}
