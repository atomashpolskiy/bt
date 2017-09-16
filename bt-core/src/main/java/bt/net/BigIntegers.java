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

package bt.net;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Utility class that provides convenient shortcuts
 * for working with java.math.BigInteger as if it was unsigned
 *
 * @since 1.2
 */
public class BigIntegers {

    /**
     * Encode an arbitrary big integer to its' binary representation,
     * without unnecessary leading zeros that are used in two's-complement form.
     *
     * @param i Arbitrary big integer
     * @param byteCount Number of bytes to use for encoding
     * @return Byte array containing the binary representation of the big integer left-padded with zeros if necessary
     * @since 1.2
     */
    public static byte[] encodeUnsigned(BigInteger i, int byteCount) {
        if (byteCount < 1) {
            throw new IllegalArgumentException("Invalid number of bytes: " + byteCount);
        }
        byte[] bytes = i.toByteArray();
        if (bytes[0] == 0) {
            // first byte is prepended for a sign bit
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        if (bytes.length > byteCount) {
            throw new IllegalStateException("Value truncation");
        }
        if (bytes.length < byteCount) {
            byte[] bytesCopy = bytes;
            bytes = new byte[byteCount];
            System.arraycopy(bytesCopy, 0, bytes, (bytes.length - bytesCopy.length), bytesCopy.length);
        }
        return bytes;
    }

    /**
     * Decode an unsigned big integer from the provided buffer.
     * Leading zeros are stripped.
     *
     * @param length Number of bytes to read from the buffer; may include leading zeros
     * @return Non-negative big integer
     * @since 1.2
     */
    public static BigInteger decodeUnsigned(ByteBuffer buffer, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Invalid number of bytes: " + length);
        } else if (buffer.remaining() < length) {
            throw new IllegalStateException("Insufficient bytes in buffer: " + buffer.remaining() + ", requested: " + length);
        }
        // strip leading zeros
        byte b;
        int i = 0;
        while ((b = buffer.get()) == 0 && ++i < length)
            ;

        // all bytes were zeros
        if (i == length) {
            return BigInteger.ZERO;
        }

        int len = length - i;
        byte[] bytes = new byte[len];
        bytes[0] = b;
        buffer.get(bytes, 1, len - 1);
        return new BigInteger(1, bytes);
    }
}
