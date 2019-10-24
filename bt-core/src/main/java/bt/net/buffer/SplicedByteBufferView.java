/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

import bt.protocol.Protocols;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SplicedByteBufferView implements ByteBufferView {

    private final ByteBuffer left;
    private final ByteBuffer right;

    private int leftOffset;
    private int leftLimit;
    private int leftCapacity;
    private int rightOffset;

    private int position;
    private int limit;
    private int capacity;

    private final byte[] shortBytes;
    private final byte[] intBytes;

    public SplicedByteBufferView(ByteBuffer left, ByteBuffer right) {
        // mandate that the order is BE, otherwise it will be impossible to read multi-byte numbers,
        // that sit on the boundary of two buffers
        if (left.order() != ByteOrder.BIG_ENDIAN || right.order() != ByteOrder.BIG_ENDIAN) {
            throw new IllegalArgumentException("Byte order must be big-endian for both buffers");
        }
        this.left = left;
        this.right = right;
        this.position = 0;
        this.leftOffset = left.position();
        this.leftLimit = left.limit();
        this.leftCapacity = leftLimit - leftOffset;
        this.rightOffset = right.position();
        this.limit = this.capacity = left.remaining() + right.remaining();
        this.shortBytes = new byte[Short.BYTES];
        this.intBytes = new byte[Integer.BYTES];
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public ByteBufferView position(int newPosition) {
        if ((newPosition > limit) || (newPosition < 0)) {
            throw new IllegalArgumentException();
        }
        position = newPosition;
        if (position >= leftCapacity) {
            left.position(leftLimit);
            right.position(rightOffset + (position - leftCapacity));
        } else {
            left.position(leftOffset + position);
            right.position(rightOffset);
        }
        return this;
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public ByteBufferView limit(int newLimit) {
        if ((newLimit > capacity) || (newLimit < 0)) {
            throw new IllegalArgumentException();
        }
        limit = newLimit;
        if (limit >= leftCapacity) {
            left.limit(leftLimit);
            right.limit(limit - leftCapacity);
        } else {
            left.limit(leftOffset + limit);
            right.limit(rightOffset);
        }
        if (position > limit) {
            position = limit;
        }
        return this;
    }

    @Override
    public boolean hasRemaining() {
        return position < limit;
    }

    @Override
    public int remaining() {
        return limit - position;
    }

    @Override
    public byte get() {
        if (position >= limit) {
            throw new BufferUnderflowException();
        }
        if (position++ >= leftCapacity) {
            return right.get();
        } else {
            return left.get();
        }
    }

    @Override
    public short getShort() {
        readBytes(shortBytes);
        return Protocols.readShort(shortBytes, 0);
    }

    @Override
    public int getInt() {
        readBytes(intBytes);
        return Protocols.readInt(intBytes, 0);
    }

    @Override
    public ByteBufferView get(byte[] dst) {
        readBytes(dst);
        return this;
    }

    private void readBytes(byte[] dst) {
        if (limit - position < dst.length) {
            throw new BufferUnderflowException();
        }
        if (left.remaining() >= dst.length) {
            left.get(dst);
        } else if (!left.hasRemaining()) {
            right.get(dst);
        } else {
            // combine from two buffers
            int bytesFromLeft = left.remaining();
            int bytesFromRight = dst.length - left.remaining();
            left.get(dst, 0, left.remaining());
            right.get(dst, bytesFromLeft, bytesFromRight);
        }
        position += dst.length;
    }
}
