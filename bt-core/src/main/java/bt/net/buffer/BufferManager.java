/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.runtime.Config;
import com.google.inject.Inject;

import java.lang.ref.SoftReference;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

public class BufferManager implements IBufferManager {

    private ConcurrentMap<Class<?>, Deque<SoftReference<?>>> releasedBuffers;

    private final int bufferSize;

    @Inject
    public BufferManager(Config config) {
        this.bufferSize = config.getNetworkBufferSize();
        this.releasedBuffers = new ConcurrentHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public BorrowedBuffer<ByteBuffer> borrowByteBuffer() {
        Deque<SoftReference<?>> deque = getReleasedBuffersDeque(ByteBuffer.class);
        SoftReference<ByteBuffer> ref;
        ByteBuffer buffer = null;
        do {
            ref = (SoftReference<ByteBuffer>) deque.pollLast();
            if (ref != null) {
                buffer = ref.get();
            }
            // check if the referenced buffer has been garbage collected
        } while (ref != null && buffer == null);

        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        } else {
            // reset buffer before re-using
            buffer.clear();
        }
        return new DefaultBorrowedBuffer<>(buffer);
    }

    private <T extends Buffer> Deque<SoftReference<?>> getReleasedBuffersDeque(Class<T> bufferType) {
        return releasedBuffers.computeIfAbsent(bufferType, it -> new LinkedBlockingDeque<>());
    }

    private class DefaultBorrowedBuffer<T extends Buffer> implements BorrowedBuffer<T> {

        private volatile T buffer;
        private final ReentrantLock lock;

        DefaultBorrowedBuffer(T buffer) {
            this.buffer = Objects.requireNonNull(buffer);
            this.lock = new ReentrantLock();
        }

        @Override
        public T lockAndGet() {
            lock.lock();
            return buffer;
        }

        @Override
        public void unlock() {
            lock.unlock();
        }

        @Override
        public void release() {
            lock.lock();
            try {
                if (buffer != null) {
                    // check if lockAndGet() has been called by the current thread and not followed by unlock()
                    if (lock.getHoldCount() > 1) {
                        throw new IllegalStateException("Buffer is locked and can't be released");
                    }
                    if (buffer != null) {
                        getReleasedBuffersDeque(ByteBuffer.class).add(new SoftReference<>(buffer));
                        buffer = null;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
