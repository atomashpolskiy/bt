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

import bt.net.Peer;
import bt.net.buffer.*;
import bt.protocol.Message;
import bt.protocol.Piece;
import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Inspired by Bip Buffer (https://www.codeproject.com/Articles/3479/The-Bip-Buffer-The-Circular-Buffer-with-a-Twist)
 */
public class InboundMessageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(InboundMessageProcessor.class);

    private final Peer peer;
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

    public InboundMessageProcessor(Peer peer,
                                   ByteBuffer buffer,
                                   MessageDeserializer deserializer,
                                   List<BufferMutator> decoders,
                                   IBufferedPieceRegistry bufferedPieceRegistry) {
        if (buffer.position() != 0 || buffer.limit() != buffer.capacity() || buffer.capacity() == 0) {
            throw new IllegalArgumentException("Illegal buffer params (position: "
                    + buffer.position() + ", limit: " + buffer.limit() + ", capacity: " + buffer.capacity() + ")");
        }
        this.peer = peer;
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
        try {
            if (regionB == null) {
                processA();
            } else {
                processAB();
            }
        } catch (Exception e) {
            printDebugInfo(e.getMessage());
            throw new RuntimeException(e);
        }
        if (LOGGER.isTraceEnabled()) {
            printDebugInfo("Finished processing inbound data");
        }
    }

    private void printDebugInfo(String message) {
        String s = "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>";
        s += "\n  Debug message: " + message;
        s += "\n  Peer: " + peer;
        s += "\n  Buffer: " + buffer;
        s += "\n  Region A: " + regionA;
        s += "\n  Region B: " + regionB;
        s += "\n  Decoding params: " + decodingView;
        s += "\n  First undisposed data offset: " + undisposedDataOffset;
        s += "\n  Message queue size: " + messageQueue.size();
        if (!bufferQueue.isEmpty()) {
            s += "\n  Undisposed data queue size: " + bufferQueue.size();
            for (BufferedDataWithOffset data : bufferQueue) {
                s += "\n   * " + data;
            }
        }
        s += "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<";
        LOGGER.trace(s);
    }

    private void processA() {
        reclaimDisposedBuffers();

        decodingView.undecodedLimit = buffer.position();
        decode();

        decodingView.unconsumedOffset += consumeA();

        // Resize region A
        regionA.setOffsetAndLimit(
                (undisposedDataOffset >= 0)
                        ? undisposedDataOffset
                        : decodingView.unconsumedOffset,
                decodingView.undecodedLimit);

        if (regionA.offset == regionA.limit) {
            // All data has been consumed, we can reset region A and decoding view
            buffer.limit(buffer.capacity());
            buffer.position(0);
            regionA.setOffsetAndLimit(0, 0);
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
                    if (message instanceof Piece) {
                        Piece piece = (Piece) message;
                        int globalOffset = buffer.position() - piece.getLength();
                        processPieceMessage(piece, bufferView.duplicate(), globalOffset);
                    }
                    // careful: post message only after having published buffered data
                    messageQueue.add(message);
                    consumed += (buffer.position() - prevPosition);
                    prevPosition = buffer.position();
                }
            }
        }
        return consumed;
    }

    private void processAB() {
        reclaimDisposedBuffers();

        decodingView.undecodedLimit = buffer.position();
        decode();

        regionB.setLimit(decodingView.undecodedLimit);

        if (undisposedDataOffset >= regionA.offset) {
            regionA.setOffset(undisposedDataOffset);
        } else if (decodingView.unconsumedOffset >= regionA.offset) {
            regionA.setOffset(decodingView.unconsumedOffset);
        } else {
            regionA.setOffset(regionA.limit);
        }

        int consumed = consumeAB();
        int consumedA, consumedB;
        if (decodingView.unconsumedOffset >= regionA.offset) {
            consumedA = Math.min(consumed, regionA.limit - decodingView.unconsumedOffset);
            consumedB = consumed - consumedA; // non-negative
            if (undisposedDataOffset < regionA.offset) {
                regionA.setOffset(regionA.offset + consumedA);
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

        if (regionA.offset == regionA.limit) {
            // Data in region A has been fully processed,
            // so now we promote region B to become region A
            if (undisposedDataOffset < 0) {
                // There's no undisposed data, so we can shrink B
                regionB.setOffset(regionB.offset + consumedB);
                if (regionB.limit == decodingView.unconsumedOffset || regionB.limit == regionB.offset) {
                    // Or even reset it, if all of it has been consumed
                    regionB.setOffsetAndLimit(0, 0);
                    decodingView.unconsumedOffset = 0;
                    decodingView.undecodedOffset = 0;
                    decodingView.undecodedLimit = 0;
                }
            } else {
                regionB.setOffset(undisposedDataOffset);
            }
            if (decodingView.unconsumedOffset == regionA.limit) {
                decodingView.unconsumedOffset = regionB.offset;
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
        int splicedBufferOffset;
        if (dbw.unconsumedOffset >= regionA.offset) {
            ByteBuffer regionAView = buffer.duplicate();
            regionAView.limit(regionA.limit);
            regionAView.position(dbw.unconsumedOffset);

            ByteBuffer regionBView = buffer.duplicate();
            regionBView.limit(dbw.undecodedOffset);
            regionBView.position(regionB.offset);

            splicedBuffer = new SplicedByteBufferView(regionAView, regionBView);
            splicedBufferOffset = dbw.unconsumedOffset;
        } else {
            ByteBuffer regionBView = buffer.duplicate();
            regionBView.limit(dbw.undecodedLimit);
            regionBView.position(dbw.unconsumedOffset);

            splicedBuffer = new DelegatingByteBufferView(regionBView);
            splicedBufferOffset = 0;
        }

        Message message;
        int consumed = 0, prevPosition = splicedBuffer.position();
        for (;;) {
            message = deserializer.deserialize(splicedBuffer);
            if (message == null) {
                break;
            } else {
                if (message instanceof Piece) {
                    Piece piece = (Piece) message;
                    int globalOffset = splicedBufferOffset + (splicedBuffer.position() - piece.getLength());
                    if (globalOffset >= regionA.limit) {
                        globalOffset = regionB.offset + (globalOffset - regionA.limit);
                    }
                    processPieceMessage(piece, splicedBuffer.duplicate(), globalOffset);
                }
                // careful: post message only after having published buffered data
                messageQueue.add(message);

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
            bufferQueue.add(new BufferedDataWithOffset(piece, bufferedData, globalOffset, buffer.remaining()));
        }
    }

    private static class Region {
        private int offset;
        private int limit;

        public Region(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            if (offset > limit) {
                throw new IllegalArgumentException("offset greater than limit: "
                        + offset + " > " + limit);
            }
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            if (limit < offset) {
                throw new IllegalArgumentException("limit smaller than offset: "
                        + limit + " < " + offset);
            }
            this.limit = limit;
        }

        public void setOffsetAndLimit(int offset, int limit) {
            if (offset < 0 || limit < 0 || limit < offset) {
                throw new IllegalArgumentException("illegal offset ("
                        + offset + ") and limit (" + limit + ")");
            }
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
        private final int pieceIndex;
        private final int pieceOffset;
        public final BufferedData buffer;
        public final int offset;
        public final int length;

        private BufferedDataWithOffset(Piece piece, BufferedData buffer, int offset, int length) {
            this.pieceIndex = piece.getPieceIndex();
            this.pieceOffset = piece.getOffset();
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("pieceIndex", pieceIndex)
                    .add("pieceOffset", pieceOffset)
                    .add("disposed", buffer.isDisposed())
                    .add("offset", offset)
                    .add("length", length)
                    .toString();
        }
    }
}
