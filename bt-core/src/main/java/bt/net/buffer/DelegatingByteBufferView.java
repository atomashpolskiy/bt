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

import com.google.common.base.MoreObjects;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class DelegatingByteBufferView implements ByteBufferView {

    private final ByteBuffer delegate;

    public DelegatingByteBufferView(ByteBuffer delegate) {
        this.delegate = delegate;
    }

    @Override
    public int position() {
        return delegate.position();
    }

    @Override
    public ByteBufferView position(int newPosition) {
        delegate.position(newPosition);
        return this;
    }

    @Override
    public int limit() {
        return delegate.limit();
    }

    @Override
    public ByteBufferView limit(int newLimit) {
        delegate.limit(newLimit);
        return this;
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public boolean hasRemaining() {
        return delegate.hasRemaining();
    }

    @Override
    public int remaining() {
        return delegate.remaining();
    }

    @Override
    public byte get() {
        return delegate.get();
    }

    @Override
    public short getShort() {
        return delegate.getShort();
    }

    @Override
    public int getInt() {
        return delegate.getInt();
    }

    @Override
    public ByteBufferView get(byte[] dst) {
        delegate.get(dst);
        return this;
    }

    @Override
    public void transferTo(ByteBuffer buffer) {
        delegate.put(buffer);
    }

    @Override
    public int transferTo(WritableByteChannel sbc) throws IOException {
        return sbc.write(delegate);
    }

    @Override
    public ByteBufferView duplicate() {
        return new DelegatingByteBufferView(delegate.duplicate());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("pos", delegate.position())
                .add("lim", delegate.limit())
                .add("cap", delegate.capacity())
                .toString();
    }
}
