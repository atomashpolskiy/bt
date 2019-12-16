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

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *<p><b>Note that this class is not a part of the public API and is a subject to change.</b></p>
 */
public class DataReaderChannel implements ReadableByteChannel {

    private final DataDescriptor dataDescriptor;
    private final Bitfield bitfield;
    private final BitSet skipped;
    private final long chunkSize;

    private int position;
    private long remaining;
    private volatile AtomicInteger limit;
    private volatile boolean closed;

    public DataReaderChannel(DataDescriptor dataDescriptor, long chunkSize) {
        this.dataDescriptor = Objects.requireNonNull(dataDescriptor);
        this.bitfield = dataDescriptor.getBitfield();
        this.skipped = bitfield.getSkippedBitmask();
        this.chunkSize = chunkSize;

        this.remaining = chunkSize;
    }

    public void init() {
        BitSet bitmask = bitfield.getBitmask();
        int limit = 0;
        while (skipped.get(limit) || bitmask.get(limit)) {
            limit++;
        }
        this.limit = new AtomicInteger(limit);
    }

    public synchronized void onPieceVerified(int pieceIndex) {
        int limit = this.limit.intValue();
        if (pieceIndex >= limit) {
            while (limit < bitfield.getPiecesTotal() && bitfield.isVerified(limit)) {
                limit++;
            }
            this.limit.set(limit);
            notifyAll();
        }
    }

    @Override
    public int read(ByteBuffer dst) {
        if (position == bitfield.getPiecesTotal()) {
            return -1;
        }

        if (position == limit.intValue()) {
            synchronized (this) {
                try {
                    while (position == limit.intValue()) {
                        wait();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpectedly interrupted", e);
                }
            }
        }

        int readTotal = 0;

        do {
            int dstPosition = dst.position();
            int dstLimit = dst.limit();

            DataRange data = dataDescriptor.getChunkDescriptors().get(position).getData();
            long[] bytesToSkip = new long[]{chunkSize - remaining};

            data.visitUnits((unit, off, lim) -> {
                long length = lim - off;

                if (bytesToSkip[0] < length) {
                    off += bytesToSkip[0];
                    length -= bytesToSkip[0];

                    if (length < dst.remaining()) {
                        dst.limit((int) (dstPosition + length));
                    }

                    try {
                        unit.readBlock(dst, off);
                    } finally {
                        dst.limit(dstLimit);
                    }
                } else {
                    bytesToSkip[0] -= length;
                }

                return dst.remaining() > 0;
            });

            int read = dst.position() - dstPosition;
            readTotal += read;
            remaining -= read;
            if (remaining == 0) {
                position++;
                remaining = chunkSize;
            }
        } while (position < limit.intValue() && dst.remaining() > 0);

        return readTotal;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
