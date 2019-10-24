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

package bt.net.pipeline;

import bt.net.buffer.BufferMutator;
import bt.net.buffer.BufferedData;
import bt.protocol.Message;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Inspired by Bip Buffer (https://www.codeproject.com/Articles/3479/The-Bip-Buffer-The-Circular-Buffer-with-a-Twist)
 */
public class RingByteBuffer {

    private final ByteBuffer buffer;
    private volatile DecodingBufferView decodingView;
    private volatile Region regionA;
    private volatile Region regionB;

    private final MessageDeserializer deserializer;
    private final List<BufferMutator> decoders;

    private final Queue<OffsetBufferedData> bufferedDataQueue;
    private final Queue<Message> messageQueue;

    public RingByteBuffer(ByteBuffer buffer,
                          MessageDeserializer deserializer,
                          List<BufferMutator> decoders) {
        this.deserializer = deserializer;
        this.decoders = decoders;
        if (buffer.position() != 0 || buffer.limit() != buffer.capacity() || buffer.capacity() == 0) {
            throw new IllegalArgumentException("Illegal buffer params (position: "
                    + buffer.position() + ", limit: " + buffer.limit() + ", capacity: " + buffer.capacity() + ")");
        }
        this.buffer = buffer;
        this.decodingView = new DecodingBufferView(0, 0, 0);
        this.regionA = new Region(0, 1);
        this.regionB = null;
        this.bufferedDataQueue = new ArrayDeque<>();
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    public Message read() {
        return messageQueue.poll();
    }

    public void processInboundData() {
        if (regionB == null) {
            processA();
        } else {
            processAB();
        }
    }

    private void processA() {
        decodeA();
        consumeA();

        // Resize region A
        regionA.offset = decodingView.decodedOffset;
        regionA.limit = decodingView.undecodedLimit;

        if (regionA.offset == regionA.limit) {
            // All data has been consumed, we can reset region A
            buffer.clear();
            regionA.offset = 0;
            regionA.limit = 1;
        } else {
            // We need to compare the amount of free space
            // to the left and to the right of region A
            // and create region B, if there's more free space
            // to the left than to the right
            int freeSpaceBeforeOffset = regionA.offset - 1;
            int freeSpaceAfterLimit = buffer.capacity() - regionA.limit;
            if (freeSpaceBeforeOffset > freeSpaceAfterLimit) {
                regionB = new Region(0, 1);
                decodingView.undecodedOffset = regionB.offset;
                decodingView.undecodedLimit = regionB.limit;
            }
        }
    }

    private void processAB() {
        decodeAB();

        int bytesLeftInRegionA = regionA.limit - regionA.offset;
        int consumed = consumeAB();
        if (consumed >= bytesLeftInRegionA) {
            // Data in region A has been fully processed,
            // so now we promote region B to become region A
            int consumedB = (consumed - bytesLeftInRegionA);
            regionB.offset += consumedB;
            regionA = regionB;
            regionB = null;
        } else {
            regionA.offset += consumed;
        }
    }

    private void decodeA() {
        DecodingBufferView dbw = decodingView;

        dbw.undecodedLimit = buffer.position();

        if (dbw.undecodedOffset < dbw.undecodedLimit - 1) {
            buffer.flip();
            decoders.forEach(mutator -> {
                buffer.position(dbw.undecodedOffset);
                mutator.mutate(buffer);
            });
            dbw.undecodedOffset = dbw.undecodedLimit;
            buffer.position(dbw.undecodedLimit);
            buffer.limit(buffer.capacity());
        }
    }

    private void decodeAB() {
        DecodingBufferView dbw = decodingView;

        dbw.undecodedLimit = buffer.position();

        if (dbw.undecodedOffset < dbw.undecodedLimit - 1) {
            buffer.flip();
            decoders.forEach(mutator -> {
                buffer.position(dbw.undecodedOffset);
                mutator.mutate(buffer);
            });
            dbw.undecodedOffset = dbw.undecodedLimit;
            buffer.position(dbw.undecodedLimit);
            // decodedOffset currently points to the start of region A,
            // and we set it to be the limit, so that the unconsumed data in region A
            // would not be overwritten
            buffer.limit(dbw.decodedOffset);
        }
    }

    private void consumeA() {
        DecodingBufferView dbw = decodingView;

        if (dbw.decodedOffset < dbw.undecodedOffset) {
            buffer.position(dbw.decodedOffset);
            buffer.limit(dbw.undecodedOffset);
            Message message;
            for (;;) {
                message = deserializer.deserialize(buffer);
                if (message == null) {
                    break;
                } else {
                    messageQueue.add(message);
                    dbw.decodedOffset = buffer.position();
                }
            }
        }
    }

    private int consumeAB() {
        DecodingBufferView dbw = decodingView;

        ByteBuffer regionAView = buffer.duplicate();
        regionAView.clear();
        regionAView.position(dbw.decodedOffset);
        regionAView.limit(regionA.limit);

        ByteBuffer regionBView = buffer.duplicate();
        regionBView.clear();
        regionBView.limit(regionB.limit);

        ByteBuffer splicedBuffer = splice(regionAView, regionBView);

        Message message;
        for (;;) {
            message = deserializer.deserialize(splicedBuffer);
            if (message == null) {
                break;
            } else {
                messageQueue.add(message);
            }
        }

        return splicedBuffer.position();
    }

    private static class OffsetBufferedData {
        public final BufferedData data;
        public final int offset;

        private OffsetBufferedData(BufferedData data, int offset) {
            this.data = data;
            this.offset = offset;
        }
    }

    private static class Region {
        public int offset;
        public int limit;

        public Region(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }
    }

    private static class DecodingBufferView {
        public int decodedOffset;
        public int undecodedOffset;
        public int undecodedLimit;

        private DecodingBufferView(int decodedOffset, int undecodedOffset, int undecodedLimit) {
            this.decodedOffset = decodedOffset;
            this.undecodedOffset = undecodedOffset;
            this.undecodedLimit = undecodedLimit;
        }
    }
}
