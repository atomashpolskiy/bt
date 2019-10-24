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

import bt.net.buffer.*;
import bt.protocol.Message;
import bt.protocol.Piece;
import com.google.common.base.MoreObjects;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Inspired by Bip Buffer (https://www.codeproject.com/Articles/3479/The-Bip-Buffer-The-Circular-Buffer-with-a-Twist)
 */
public class InboundMessageProcessor {

    private final ByteBuffer buffer;
    private final ByteBufferView bufferView;
    private final DecodingBufferView decodingView;
    private Region regionA;
    private Region regionB;
    private int undisposedDataOffset = -1;

    private final MessageDeserializer deserializer;
    private final List<BufferMutator> decoders;
    private final IBufferedPieceRegistry bufferedPieceRegistry;

    private final Queue<Message> messageQueue;
    private final Queue<BufferedDataWithOffset> bufferQueue;

    public InboundMessageProcessor(ByteBuffer buffer,
                                   MessageDeserializer deserializer,
                                   List<BufferMutator> decoders,
                                   IBufferedPieceRegistry bufferedPieceRegistry) {
        if (buffer.position() != 0 || buffer.limit() != buffer.capacity() || buffer.capacity() == 0) {
            throw new IllegalArgumentException("Illegal buffer params (position: "
                    + buffer.position() + ", limit: " + buffer.limit() + ", capacity: " + buffer.capacity() + ")");
        }
        this.buffer = buffer;
        this.deserializer = deserializer;
        this.decoders = decoders;
        this.bufferedPieceRegistry = bufferedPieceRegistry;

        this.bufferView = new DelegatingByteBufferView(buffer);
        this.decodingView = new DecodingBufferView(0, 0, 0);
        this.regionA = new Region(0, 0);
        this.regionB = null;
        this.messageQueue = new LinkedBlockingQueue<>();
        this.bufferQueue = new ArrayDeque<>();
    }

    public Message pollMessage() {
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
        reclaimDisposedBuffersA();
        decodeA();
        consumeA();

        // Resize region A
        regionA.offset = (undisposedDataOffset >= 0)
                ? Math.min(decodingView.decodedOffset, undisposedDataOffset)
                : decodingView.decodedOffset;
        regionA.limit = decodingView.undecodedLimit;

        if (regionA.offset == regionA.limit) {
            // All data has been consumed, we can reset region A and decoding view
            bufferView.position(0);
            bufferView.limit(bufferView.capacity());
            regionA.offset = 0;
            regionA.limit = 0;
            decodingView.decodedOffset = 0;
            decodingView.undecodedOffset = 0;
            decodingView.undecodedLimit = 0;
        } else {
            // We need to compare the amount of free space
            // to the left and to the right of region A
            // and create region B, if there's more free space
            // to the left than to the right
            int freeSpaceBeforeOffset = regionA.offset - 1;
            int freeSpaceAfterLimit = buffer.capacity() - regionA.limit;
            if (freeSpaceBeforeOffset > freeSpaceAfterLimit) {
                regionB = new Region(0, 0);
                decodingView.undecodedOffset = regionB.offset;
                decodingView.undecodedLimit = regionB.limit;
                // Adjust buffer's position and limit,
                // so that it would be possible to append new data to it
                bufferView.position(0);
                bufferView.limit(regionA.offset);
            }
        }
    }

    private void reclaimDisposedBuffersA() {
        BufferedDataWithOffset buffer;
        while ((buffer = bufferQueue.peek()) != null) {
            if (buffer.buffer.isDisposed()) {
                bufferQueue.remove();
                if (bufferQueue.isEmpty()) {
                    undisposedDataOffset = -1;
                }
            } else {
                undisposedDataOffset = buffer.offset;
                break;
            }
        }
    }

    private void processAB() {
        reclaimDisposedBuffersAB();
        decodeAB();

        // Adjust B's limit to account for new data
        regionB.limit = buffer.position();

        int bytesLeftInRegionA = regionA.limit - regionA.offset;
        int consumed = consumeAB();
        if (consumed >= bytesLeftInRegionA) {
            // Data in region A has been fully processed,
            // so now we promote region B to become region A
            int consumedB = (consumed - bytesLeftInRegionA);
            regionB.offset += consumedB;
            regionA = regionB;
            regionB = null;
            bufferView.position(regionA.offset);
            bufferView.limit(bufferView.capacity());
            decodingView.decodedOffset = regionA.offset;
            decodingView.undecodedOffset = regionA.offset;
            decodingView.undecodedLimit = regionA.offset;
        } else {
            regionA.offset += consumed;
        }
    }

    private void reclaimDisposedBuffersAB() {
        BufferedDataWithOffset buffer;
        while ((buffer = bufferQueue.peek()) != null) {
            if (buffer.buffer.isDisposed()) {
                bufferQueue.remove();
                if (bufferQueue.isEmpty()) {
                    undisposedDataOffset = -1;
                } else {
                    int bytesLeftInRegionA = regionA.limit - regionA.offset;
                    if (buffer.length <= bytesLeftInRegionA) {
                        regionA.offset += buffer.length;
                    } else {
                        regionA.offset = regionA.limit;
                        regionB.offset += (buffer.length - bytesLeftInRegionA);
                    }
                }
            } else {
                undisposedDataOffset = buffer.offset;
                break;
            }
        }
    }

    private void decodeA() {
        DecodingBufferView dbw = decodingView;

        dbw.undecodedLimit = buffer.position();

        if (dbw.undecodedOffset < dbw.undecodedLimit) {
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

        if (dbw.undecodedOffset < dbw.undecodedLimit) {
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

        int initialPosition = buffer.position();
        if (dbw.decodedOffset < dbw.undecodedOffset) {
            bufferView.position(dbw.decodedOffset);
            bufferView.limit(dbw.undecodedOffset);
            Message message;
            for (;;) {
                message = deserializer.deserialize(bufferView);
                if (message == null) {
                    buffer.limit(buffer.capacity());
                    buffer.position(initialPosition);
                    break;
                } else {
                    messageQueue.add(message);
                    if (message instanceof Piece) {
                        Piece piece = (Piece) message;
                        int globalOffset = buffer.position() - piece.getLength();
                        processPieceMessage(piece, bufferView.duplicate(), globalOffset);
                    }
                    dbw.decodedOffset = buffer.position();
                }
            }
        }
    }

    private int consumeAB() {
        DecodingBufferView dbw = decodingView;

        int initialOffset;
        ByteBufferView splicedBuffer;
        if (regionA.offset == regionA.limit) {
            ByteBuffer regionBView = buffer.duplicate();
            regionBView.position(regionB.offset);
            regionBView.limit(regionB.limit);

            initialOffset = regionB.offset;
            splicedBuffer = new DelegatingByteBufferView(regionBView);
        } else {
            ByteBuffer regionAView = buffer.duplicate();
            regionAView.limit(regionA.limit);
            regionAView.position(dbw.decodedOffset);

            ByteBuffer regionBView = buffer.duplicate();
            regionBView.limit(regionB.limit);
            regionBView.position(0);

            initialOffset = dbw.decodedOffset;
            splicedBuffer = new SplicedByteBufferView(regionAView, regionBView);
        }

        Message message;
        for (;;) {
            message = deserializer.deserialize(splicedBuffer);
            if (message == null) {
                break;
            } else {
                messageQueue.add(message);
                if (message instanceof Piece) {
                    Piece piece = (Piece) message;
                    int globalOffset = initialOffset + (splicedBuffer.position() - piece.getLength());
                    if (globalOffset >= regionA.limit) {
                        globalOffset = splicedBuffer.position() - (regionA.limit - regionA.offset);
                    }
                    processPieceMessage(piece, splicedBuffer.duplicate(), globalOffset);
                }
            }
        }

        return splicedBuffer.position();
    }

    private void processPieceMessage(Piece piece, ByteBufferView buffer, int globalOffset) {
        int offset = buffer.position() - piece.getLength();
        buffer.limit(buffer.position());
        buffer.position(offset);

        BufferedData bufferedData = new BufferedData(buffer);
        boolean added = bufferedPieceRegistry.addBufferedPiece(piece.getPieceIndex(), piece.getOffset(), bufferedData);
        if (added) {
            if (bufferQueue.isEmpty()) {
                undisposedDataOffset = globalOffset;
            }
            bufferQueue.add(new BufferedDataWithOffset(bufferedData, globalOffset, buffer.remaining()));
        }
    }

    private static class Region {
        public int offset;
        public int limit;

        public Region(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("offset", offset)
                    .add("limit", limit)
                    .toString();
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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("decodedOffset", decodedOffset)
                    .add("undecodedOffset", undecodedOffset)
                    .add("undecodedLimit", undecodedLimit)
                    .toString();
        }
    }

    private static class BufferedDataWithOffset {
        public final BufferedData buffer;
        public final int offset;
        public final int length;

        private BufferedDataWithOffset(BufferedData buffer, int offset, int length) {
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
        }
    }
}
