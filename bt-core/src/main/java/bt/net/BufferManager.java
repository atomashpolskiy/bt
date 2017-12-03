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

import bt.runtime.Config;
import com.google.inject.Inject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

// TODO: unassociate buffers from disconnected peers; re-use existing unassociated buffers; dispose of excessive buffers
public class BufferManager implements IBufferManager {

    private final ConcurrentMap<Peer, ByteBuffer> inBuffers;
    private final ConcurrentMap<Peer, ByteBuffer> outBuffers;

    private final int bufferSize;
    private final ReentrantLock allocationLock;

    @Inject
    public BufferManager(Config config) {
        this.inBuffers = new ConcurrentHashMap<>();
        this.outBuffers = new ConcurrentHashMap<>();
        this.bufferSize = getBufferSize(config.getMaxTransferBlockSize());
        this.allocationLock = new ReentrantLock();
    }

    private static int getBufferSize(long maxTransferBlockSize) {
        if (maxTransferBlockSize > ((Integer.MAX_VALUE - 13) / 2)) {
            throw new IllegalArgumentException("Transfer block size is too large: " + maxTransferBlockSize);
        }
        return (int) (maxTransferBlockSize) * 2;
    }

    @Override
    public ByteBuffer getInBuffer(Peer peer) {
        return getOrAllocateBuffer(peer, inBuffers);
    }

    @Override
    public ByteBuffer getOutBuffer(Peer peer) {
        return getOrAllocateBuffer(peer, outBuffers);
    }

    private ByteBuffer getOrAllocateBuffer(Peer peer, Map<Peer, ByteBuffer> existingBuffers) {
        ByteBuffer buffer = existingBuffers.get(peer);
        if (buffer == null) {
            allocationLock.lock();
            try {
                buffer = existingBuffers.get(peer);
                if (buffer == null) {
                    buffer = ByteBuffer.allocateDirect(bufferSize);
                    existingBuffers.put(peer, buffer);
                }
            } finally {
                allocationLock.unlock();
            }
        }
        return buffer;
    }
}
