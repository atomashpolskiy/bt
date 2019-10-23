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
    private volatile DecodingBufferView decodingBufferView;
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
        if (buffer.position() != 0 || buffer.limit() != buffer.capacity()) {
            throw new IllegalArgumentException("Illegal buffer params (position: "
                    + buffer.position() + ", limit: " + buffer.limit() + ")");
        }
        this.buffer = buffer;
        this.decodingBufferView = new DecodingBufferView(buffer.duplicate());
        this.regionA = new Region(0, 1);
        this.regionB = null;
        this.bufferedDataQueue = new ArrayDeque<>();
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    public Message read() {
        // 1. Decode new messages
        // Decoding buffer is currently pointing at the end of the most recently decoded data.
        // Thus, from the buffer's position we can deduce,
        // which region is currently used for decoding data
        if (decodingBufferView.buffer.position() >= regionA.offset) {
            // Current region for decoding data is A
            decodingBufferView.limit(regionA.limit);
            while (decodingBufferView.hasRemaining()) {
                if (/*Nothing consumed*/true) {
                    break;
                }
            }
        } else {
            // Current region for decoding data is B
            decodingBufferView.limit(regionB.limit);
            while (decodingBufferView.hasRemaining()) {
                if (/*Nothing consumed*/true) {
                    break;
                }
            }
        }

        // Buffer is currently pointing at the end of the most recently received data.
        // Thus, from the buffer's position we can deduce,
        // which region is currently used for receiving data
        if (buffer.position() >= regionA.offset) {
            // Current region for receiving data is A
            regionA.limit = buffer.position();
            // If region B is not created yet, then we need to compare
            // the amount of free space to the left and to the right of region A
            // and create region B, if there's more free space to the left than to the right
            if (regionB == null) {
                int freeSpaceBeforeOffset = regionA.offset - 1;
                int freeSpaceAfterLimit = buffer.capacity() - regionA.limit;
                if (freeSpaceBeforeOffset > freeSpaceAfterLimit) {
                    regionB = new Region(0, 1);
                    // Finally, if any partial undecoded data is left in region A,
                    // we need to splice regions A and B into one "buffer view",
                    // so that the decoder could complete its' job, when new data arrives
                    if (regionA.offset != regionA.limit) {
                        ByteBuffer regionAView = buffer.duplicate();
                        regionAView.clear();
                        regionAView.position(regionA.offset);
                        regionAView.limit(regionA.limit);

                        ByteBuffer regionBView = buffer.duplicate();
                        regionBView.clear();
                        regionBView.position(regionB.offset);
                        regionBView.limit(regionA.offset);

                        decodingBufferView = new DecodingBufferView(splice(regionAView, regionBView));
                    }
                }
            }
        } else {
            // Current region for receiving data is B
            // Check, if region A is exhausted
            if (regionA.offset == regionA.limit) {
                // If so, from now on region B is the main region
                // (i.e. it's renamed to region A)
                regionA = regionB;
                // Get rid of explicit region B
                regionB = null;
                // Replace spliced decoding buffer with ordinary buffer,
                // based exclusively on region A
                decodingBufferView = new DecodingBufferView(buffer.duplicate());
                decodingBufferView.buffer.clear();
                decodingBufferView.buffer.position(regionA.offset);
            }
        }


        OffsetBufferedData firstUndisposedData;
        while ((firstUndisposedData = bufferedDataQueue.peek()) != null) {
            if (!firstUndisposedData.data.isDisposed()) {
                break;
            }
            bufferedDataQueue.remove();
        }


        if (firstUndisposedData == null) {

        } else if (consumedDataOffset >= firstUndisposedData.offset) {

        } else {

        }
    }

    private Message decode(Region region) {
        DecodingBufferView dbw = this.decodingBufferView;

        int undecodedDataLimit = dbw.buffer.position();
        if (dbw.undecodedDataOffset <= undecodedDataLimit) {
            if (dbw.undecodedDataOffset < undecodedDataLimit) {
                dbw.buffer.flip();
                decoders.forEach(mutator -> {
                    dbw.buffer.position(dbw.undecodedDataOffset);
                    mutator.mutate(dbw.buffer);
                });
                dbw.undecodedDataOffset = undecodedDataLimit;
                dbw.buffer.clear();
                dbw.buffer.position(dbw.undecodedDataOffset);
            }

            if (dbw.decodedDataOffset < dbw.undecodedDataOffset) {
                dbw.buffer.position(dbw.decodedDataOffset);
                dbw.buffer.limit(dbw.undecodedDataOffset);
                Message message;
                for (; ; ) {
                    message = deserializer.deserialize(dbw.buffer);
                    if (message == null) {
                        break;
                    } else {
                        messageQueue.add(message);
                        if (/*message has buffered data*/) {
                            BufferedData data = null;
                            int offset = dbw.buffer.position() - data.length();
                            bufferedDataQueue.add(new OffsetBufferedData(data, offset));
                            if (bufferedDataQueue.size() == 1) {
                                // can switch
                            }
                        } else {
                            dbw.decodedDataOffset = dbw.buffer.position();
                        }
                    }
                }

                dbw.buffer.clear();
                dbw.buffer.position(dbw.undecodedDataOffset);
                if (!dbw.buffer.hasRemaining()) {
                    dbw.buffer.position(dbw.decodedDataOffset);
                    dbw.buffer.compact();
                    dbw.undecodedDataOffset -= dbw.decodedDataOffset;
                    dbw.buffer.position(dbw.undecodedDataOffset);
                    dbw.decodedDataOffset = 0;
                }
            } else if (dbw.decodedDataOffset == dbw.undecodedDataOffset) {
                dbw.buffer.clear();
            } if (dbw.decodedDataOffset > dbw.undecodedDataOffset) {
                throw new IllegalStateException("decodedDataOffset > undecodedDataOffset: " + dbw.decodedDataOffset + " > " + dbw.undecodedDataOffset);
            }
        } else if (dbw.undecodedDataOffset > undecodedDataLimit) {
            throw new IllegalStateException("undecodedDataOffset > undecodedDataLimit: " + dbw.undecodedDataOffset + " > " + undecodedDataLimit);
        }
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
        public final ByteBuffer buffer;
        public int decodedDataOffset;
        public int undecodedDataOffset;

        private DecodingBufferView(ByteBuffer buffer) {
            this.buffer = buffer;
            this.decodedDataOffset = buffer.position();
            this.undecodedDataOffset = buffer.position();
        }
    }
}
