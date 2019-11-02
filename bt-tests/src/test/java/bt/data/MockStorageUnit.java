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

package bt.data;

import bt.net.buffer.ByteBufferView;

import java.nio.ByteBuffer;

public class MockStorageUnit implements StorageUnit {

    private final long capacity;
    private final ByteBuffer file;

    private long size;

    public MockStorageUnit(long capacity) {
        if (capacity < 0 || capacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Illegal capacity: " + capacity);
        }
        this.capacity = capacity;
        this.file = ByteBuffer.allocate((int) capacity);
    }

    @Override
    public int readBlock(ByteBuffer buffer, long offset) {
        setOffset(offset);
        int position = buffer.position();
        buffer.put(file);
        return buffer.position() - position;
    }

    @Override
    public void readBlockFully(ByteBuffer buffer, long offset) {
        readBlock(buffer, offset);
    }

    @Override
    public int writeBlock(ByteBuffer buffer, long offset) {
        setOffset(offset);
        int position = buffer.position();
        file.put(buffer);
        updateSize();
        return buffer.position() - position;
    }

    @Override
    public void writeBlockFully(ByteBuffer buffer, long offset) {
        writeBlock(buffer, offset);
    }

    @Override
    public int writeBlock(ByteBufferView buffer, long offset) {
        setOffset(offset);
        int position = buffer.position();
        buffer.transferTo(file);
        updateSize();
        return buffer.position() - position;
    }

    @Override
    public void writeBlockFully(ByteBufferView buffer, long offset) {
        writeBlock(buffer, offset);
    }

    private void setOffset(long offset) {
        if (offset < 0 || offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Illegal offset: " + offset);
        }
        file.clear();
        file.position((int) offset);
    }

    private void updateSize() {
        size = Math.max(size, file.position());
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public long size() {
        return capacity;
    }

    @Override
    public void close() {
        // do nothing
    }
}
