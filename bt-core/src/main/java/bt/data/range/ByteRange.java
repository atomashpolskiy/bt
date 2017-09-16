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

package bt.data.range;

import java.nio.ByteBuffer;

/**
 * Binary data range.
 *
 * @since 1.3
 */
public class ByteRange implements Range<ByteRange> {

    private final ByteBuffer buffer;

    /**
     * Create a binary range from a byte array.
     *
     * @param bytes Byte array
     * @since 1.3
     */
    public ByteRange(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    /**
     * Create a binary range from a subrange of a byte array.
     *
     * @param bytes Byte array
     * @param offset Offset in {@code bytes}, inclusive
     * @param limit Limit in {@code bytes}, exclusive; must not be larger than {@code bytes.length}
     *
     * @since 1.3
     */
    public ByteRange(byte[] bytes, int offset, int limit) {
        int available = bytes.length;
        if (available == 0) {
            throw new IllegalArgumentException("Empty byte array");
        }
        checkOffsetAndLimit(offset, limit, available);

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.limit(limit);
        buf.position(offset);
        this.buffer = buf;
    }

    /**
     * Create a binary range from a byte buffer.
     *
     * @since 1.3
     */
    public ByteRange(ByteBuffer buffer) {
        if (buffer.remaining() == 0) {
            throw new IllegalArgumentException("Empty buffer");
        }
        this.buffer = buffer;
    }

    @Override
    public long length() {
        return buffer.remaining();
    }

    @Override
    public ByteRange getSubrange(long offset, long length) {
        long available = length();
        if (offset == 0 && length == available) {
            return this;
        }

        checkOffset(offset, available);
        checkLength(offset, length, available);

        ByteBuffer copy = buffer.duplicate();
        copy.position((int) offset);
        copy.limit((int) (offset + length));

        return new ByteRange(copy);
    }

    @Override
    public ByteRange getSubrange(long offset) {
        if (offset == 0) {
            return this;
        }

        checkOffset(offset, length());

        ByteBuffer copy = buffer.duplicate();
        copy.position((int) offset);

        return new ByteRange(copy);
    }

    @Override
    public byte[] getBytes() {
        int position = buffer.position();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(position);
        return bytes;
    }

    @Override
    public void putBytes(byte[] block) {
        if (block.length == 0) {
            return;
        } else if (block.length > length()) {
            throw new IllegalArgumentException(String.format(
                    "Data does not fit in this range (expected max %d bytes, actual: %d)", length(), block.length));
        }
        int position = buffer.position();
        buffer.put(block);
        buffer.position(position);
    }

    private static void checkOffsetAndLimit(long offset, int limit, int available) {
        checkOffset(offset, available);
        checkLimit(limit, available);
        if (offset >= limit) {
            throw new IllegalArgumentException("Offset is larger than or equal to limit (offset: " + offset + ", limit: " + limit + ")");
        }
    }

    private static void checkOffset(long offset, long available) {
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Offset is too large: " + offset);
        }
        if (offset < 0 || offset > available - 1) {
            throw new IllegalArgumentException("Invalid offset: " + offset +
                    ", expected 0.." + (available - 1));
        }
    }

    private static void checkLimit(long limit, long available) {
        if (limit > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Limit is too large: " + limit);
        }
        if (limit < 0 || limit > available) {
            throw new IllegalArgumentException("Invalid limit: " + limit + ", expected 1.." + available);
        }
    }

    private static void checkLength(long offset, long length, long available) {
        if (length < 0) {
            throw new IllegalArgumentException("Requested negative length: " + length);
        }
        long maxlen = Math.min(available - offset, Integer.MAX_VALUE - offset);
        if (length == 0) {
            throw new IllegalArgumentException("Requested empty subrange, expected length of 1.." + maxlen);
        }
        if (length > maxlen) {
            throw new IllegalArgumentException("Insufficient data: requested " + length + " bytes, expected 1.." + maxlen);
        }
    }
}
