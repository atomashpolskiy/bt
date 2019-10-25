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
        reclaimDisposedBuffers();

        decodingView.undecodedLimit = buffer.position();
        decode();

        decodingView.unconsumedOffset += consumeA();

        // Resize region A
        regionA.offset = (undisposedDataOffset >= 0)
                ? undisposedDataOffset
                : decodingView.unconsumedOffset;
        regionA.limit = decodingView.undecodedLimit;

        if (regionA.offset == regionA.limit) {
            // All data has been consumed, we can reset region A and decoding view
            buffer.limit(buffer.capacity());
            buffer.position(0);
            regionA.offset = 0;
            regionA.limit = 0;
            decodingView.unconsumedOffset = 0;
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
                buffer.limit(regionA.offset - 1);
                buffer.position(0);
            } else {
                buffer.limit(buffer.capacity());
                buffer.position(decodingView.undecodedLimit);
            }
        }
    }

    private void reclaimDisposedBuffers() {
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

    private void decode() {
        DecodingBufferView dbw = decodingView;

        if (dbw.undecodedOffset < dbw.undecodedLimit) {
            buffer.flip();
            decoders.forEach(mutator -> {
                buffer.position(dbw.undecodedOffset);
                mutator.mutate(buffer);
            });
            dbw.undecodedOffset = dbw.undecodedLimit;
        }
    }

    private int consumeA() {
        DecodingBufferView dbw = decodingView;

        int consumed = 0;
        if (dbw.unconsumedOffset < dbw.undecodedOffset) {
            bufferView.limit(dbw.undecodedOffset);
            bufferView.position(dbw.unconsumedOffset);

            Message message;
            int prevPosition = buffer.position();
            for (;;) {
                message = deserializer.deserialize(bufferView);
                if (message == null) {
                    break;
                } else {
                    messageQueue.add(message);
                    if (message instanceof Piece) {
                        Piece piece = (Piece) message;
                        int globalOffset = buffer.position() - piece.getLength();
                        processPieceMessage(piece, bufferView.duplicate(), globalOffset);
                    }
                    consumed += (buffer.position() - prevPosition);
                    prevPosition = buffer.position();
                }
            }
        }
        return consumed;
    }

    private void processAB() {
        reclaimDisposedBuffers();

        if (undisposedDataOffset >= regionA.offset) {
            regionA.offset = undisposedDataOffset;
        } else if (decodingView.unconsumedOffset >= regionA.offset) {
            regionA.offset = decodingView.unconsumedOffset;
        } else {
            regionA.offset = regionA.limit;
        }

        decodingView.undecodedLimit = buffer.position();
        decode();

        int consumed = consumeAB();
        int consumedA, consumedB;
        if (decodingView.unconsumedOffset >= regionA.offset) {
            consumedA = Math.min(consumed, regionA.limit - decodingView.unconsumedOffset);
            consumedB = consumed - consumedA; // non-negative
            if (undisposedDataOffset < regionA.offset) {
                regionA.offset += consumedA;
            }
            if (consumedB > 0) {
                decodingView.unconsumedOffset = regionB.offset + consumedB;
            } else {
                decodingView.unconsumedOffset += consumedA;
            }
        } else {
            consumedB = consumed;
            decodingView.unconsumedOffset += consumedB;
        }
        regionB.limit = decodingView.undecodedLimit;

        if (regionA.offset == regionA.limit) {
            // Data in region A has been fully processed,
            // so now we promote region B to become region A
            if (undisposedDataOffset < 0) {
                // There's no undisposed data, so we can shrink B
                regionB.offset += consumedB;
                if (regionB.limit == decodingView.unconsumedOffset || regionB.limit == regionB.offset) {
                    // Or even reset it, if all of it has been consumed
                    regionB.offset = 0;
                    regionB.limit = buffer.capacity();
                    decodingView.unconsumedOffset = 0;
                    decodingView.undecodedOffset = 0;
                    decodingView.undecodedLimit = 0;
                }
            } else {
                regionB.offset = undisposedDataOffset;
            }
            regionA = regionB;
            regionB = null;
            buffer.limit(buffer.capacity());
            buffer.position(decodingView.undecodedLimit);
        } else {
            buffer.limit(regionA.offset - 1);
            buffer.position(decodingView.undecodedLimit);
        }
    }

    private int consumeAB() {
        DecodingBufferView dbw = decodingView;

        ByteBufferView splicedBuffer;
        if (dbw.unconsumedOffset >= regionA.offset) {
            ByteBuffer regionAView = buffer.duplicate();
            regionAView.limit(regionA.limit);
            regionAView.position(dbw.unconsumedOffset);

            ByteBuffer regionBView = buffer.duplicate();
            regionBView.limit(dbw.undecodedOffset);
            regionBView.position(0);

            splicedBuffer = new SplicedByteBufferView(regionAView, regionBView);
        } else {
            ByteBuffer regionBView = buffer.duplicate();
            regionBView.limit(dbw.undecodedLimit);
            regionBView.position(dbw.unconsumedOffset);

            splicedBuffer = new DelegatingByteBufferView(regionBView);
        }

        Message message;
        int consumed = 0, prevPosition = splicedBuffer.position();
        for (;;) {
            message = deserializer.deserialize(splicedBuffer);
            if (message == null) {
                break;
            } else {
                messageQueue.add(message);
                if (message instanceof Piece) {
                    Piece piece = (Piece) message;
                    int globalOffset = dbw.unconsumedOffset + (splicedBuffer.position() - piece.getLength());
                    if (globalOffset >= regionA.limit) {
                        globalOffset -= regionA.limit;
                    }
                    processPieceMessage(piece, splicedBuffer.duplicate(), globalOffset);
                }
                consumed += (splicedBuffer.position() - prevPosition);
                prevPosition = splicedBuffer.position();
            }
        }

        return consumed;
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
        public int unconsumedOffset;
        public int undecodedOffset;
        public int undecodedLimit;

        private DecodingBufferView(int unconsumedOffset, int undecodedOffset, int undecodedLimit) {
            this.unconsumedOffset = unconsumedOffset;
            this.undecodedOffset = undecodedOffset;
            this.undecodedLimit = undecodedLimit;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("unconsumedOffset", unconsumedOffset)
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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("disposed", buffer.isDisposed())
                    .add("offset", offset)
                    .add("length", length)
                    .toString();
        }
    }
}
