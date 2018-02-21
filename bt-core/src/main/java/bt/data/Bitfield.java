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

package bt.data;

import bt.BtException;
import bt.protocol.BitOrder;
import bt.protocol.Protocols;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Status of torrent's data.
 *
 * Instances of this class are thread-safe.
 *
 * @since 1.0
 */
public class Bitfield {

    // TODO: use EMPTY and PARTIAL instead of INCOMPLETE
    /**
     * Status of a particular piece.
     *
     * @since 1.0
     */
    public enum PieceStatus {
        /*EMPTY, PARTIAL,*/INCOMPLETE, COMPLETE, COMPLETE_VERIFIED
    }

    /**
     * Bitmask indicating availability of pieces.
     * If the n-th bit is set, then the n-th piece is complete and verified.
     */
    private final BitSet bitmask;

    /**
     * Bitmask indicating pieces that should be skipped.
     * If the n-th bit is set, then the n-th piece should be skipped.
     */
    private volatile BitSet skipped;

    /**
     * Total number of pieces in torrent.
     */
    private final int piecesTotal;

    /**
     * List of torrent's chunk descriptors.
     * Absent when this Bitfield instance is describing data that some peer has.
     */
    private final Optional<List<ChunkDescriptor>> chunks;

    private final ReentrantLock lock;

    /**
     * Creates "local" bitfield from a list of chunk descriptors.
     *
     * @param chunks List of torrent's chunk descriptors.
     * @since 1.0
     */
    public Bitfield(List<ChunkDescriptor> chunks) {
        this.piecesTotal = chunks.size();
        this.bitmask = new BitSet(chunks.size());
        this.chunks = Optional.of(chunks);
        this.lock = new ReentrantLock();
    }

    /**
     * Creates empty bitfield.
     * Useful when peer does not communicate its' bitfield (e.g. when he has no data).
     *
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.0
     */
    public Bitfield(int piecesTotal) {
        this.piecesTotal = piecesTotal;
        this.bitmask = new BitSet(piecesTotal);
        this.chunks = Optional.empty();
        this.lock = new ReentrantLock();
    }

    /**
     * Creates bitfield based on a bitmask.
     * Used for creating peers' bitfields.
     *
     * Bitmask must be in the format described in BEP-3 (little-endian order of bits).
     *
     * @param value Bitmask that describes status of all pieces.
     *              If position i is set to 1, then piece with index i is complete and verified.
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.0
     * @deprecated since 1.7 in favor of {@link #Bitfield(byte[], BitOrder, int)}
     */
    @Deprecated
    public Bitfield(byte[] value, int piecesTotal) {
        this(value, BitOrder.LITTLE_ENDIAN, piecesTotal);
    }

    /**
     * Creates bitfield based on a bitmask.
     * Used for creating peers' bitfields.
     *
     * @param value Bitmask that describes status of all pieces.
     *              If position i is set to 1, then piece with index i is complete and verified.
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.7
     */
    public Bitfield(byte[] value, BitOrder bitOrder, int piecesTotal) {
        this.piecesTotal = piecesTotal;
        this.bitmask = createBitmask(value, bitOrder, piecesTotal);
        this.chunks = Optional.empty();
        this.lock = new ReentrantLock();
    }

    private static BitSet createBitmask(byte[] bytes, BitOrder bitOrder, int piecesTotal) {
        int expectedBitmaskLength = getBitmaskLength(piecesTotal);
        if (bytes.length != expectedBitmaskLength) {
            throw new IllegalArgumentException("Invalid bitfield: total (" + piecesTotal +
                    "), bitmask length (" + bytes.length + "). Expected bitmask length: " + expectedBitmaskLength);
        }

        if (bitOrder == BitOrder.LITTLE_ENDIAN) {
            bytes = Protocols.reverseBits(bytes);
        }

        BitSet bitmask = new BitSet(piecesTotal);
        for (int i = 0; i < piecesTotal; i++) {
            if (Protocols.isSet(bytes, BitOrder.BIG_ENDIAN, i)) {
                bitmask.set(i);
            }
        }
        return bitmask;
    }

    private static int getBitmaskLength(int piecesTotal) {
        return (int) Math.ceil(piecesTotal / 8d);
    }

    /**
     * @return Bitmask that describes status of all pieces.
     *         If the n-th bit is set, then the n-th piece
     *         is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.7
     */
    public BitSet getBitmask() {
        lock.lock();
        try {
            return Protocols.copyOf(bitmask);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param bitOrder Order of bits to use to create the byte array
     * @return Bitmask that describes status of all pieces.
     *         If the n-th bit is set, then the n-th piece
     *         is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.7
     */
    public byte[] toByteArray(BitOrder bitOrder) {
        byte[] bytes;
        boolean truncated = false;

        lock.lock();
        try {
            bytes = bitmask.toByteArray();
            truncated = (bitmask.length() < piecesTotal);
        } finally {
            lock.unlock();
        }

        if (bitOrder == BitOrder.LITTLE_ENDIAN) {
            bytes = Protocols.reverseBits(bytes);
        }
        if (truncated) {
            byte[] arr = new byte[getBitmaskLength(piecesTotal)];
            System.arraycopy(bytes, 0, arr, 0, bytes.length);
            return arr;
        } else {
            return bytes;
        }
    }

    /**
     * @return Total number of pieces in torrent.
     * @since 1.0
     */
    public int getPiecesTotal() {
        return piecesTotal;
    }

    /**
     * @return Number of pieces that have status {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.0
     */
    public int getPiecesComplete() {
        lock.lock();
        try {
            return bitmask.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that have status different {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.7
     */
    public int getPiecesIncomplete() {
        lock.lock();
        try {
            return getPiecesTotal() - bitmask.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that have status different from {@link PieceStatus#COMPLETE_VERIFIED}
     *         and should NOT be skipped.
     * @since 1.0
     */
    public int getPiecesRemaining() {
        lock.lock();
        try {
            if (skipped == null) {
                return getPiecesTotal() - getPiecesComplete();
            } else {
                BitSet bitmask = getBitmask();
                bitmask.or(skipped);
                return getPiecesTotal() - bitmask.cardinality();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that should be skipped
     * @since 1.7
     */
    public int getPiecesSkipped() {
        if (skipped == null) {
            return 0;
        }

        lock.lock();
        try {
            return skipped.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that should NOT be skipped
     * @since 1.7
     */
    public int getPiecesNotSkipped() {
        if (skipped == null) {
            return piecesTotal;
        }

        lock.lock();
        try {
            return piecesTotal - skipped.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param pieceIndex Piece index (0-based)
     * @return Status of the corresponding piece.
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public PieceStatus getPieceStatus(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        PieceStatus status;

        boolean verified;
        lock.lock();
        try {
            verified = this.bitmask.get(pieceIndex);
        } finally {
            lock.unlock();
        }

        if (verified) {
            status = PieceStatus.COMPLETE_VERIFIED;
        } else if (chunks.isPresent()) {
            ChunkDescriptor chunk = chunks.get().get(pieceIndex);
            if (chunk.isComplete()) {
                status = PieceStatus.COMPLETE;
            } else {
                status = PieceStatus.INCOMPLETE;
            }
        } else {
            status = PieceStatus.INCOMPLETE;
        }

        return status;
    }

    /**
     * Shortcut method to find out if the piece has been downloaded.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded
     * @since 1.1
     */
    public boolean isComplete(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return (pieceStatus == PieceStatus.COMPLETE || pieceStatus == PieceStatus.COMPLETE_VERIFIED);
    }

    /**
     * Shortcut method to find out if the piece has been downloaded and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded and verified
     * @since 1.1
     */
    public boolean isVerified(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return pieceStatus == PieceStatus.COMPLETE_VERIFIED;
    }

    /**
     * Mark piece as complete and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public void markVerified(int pieceIndex) {
        assertChunkComplete(pieceIndex);

        lock.lock();
        try {
            bitmask.set(pieceIndex);
        } finally {
            lock.unlock();
        }
    }

    private void assertChunkComplete(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        if (chunks.isPresent()) {
            if (!chunks.get().get(pieceIndex).isComplete()) {
                throw new IllegalStateException("Chunk is not complete: " + pieceIndex);
            }
        }
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= getPiecesTotal()) {
            throw new BtException("Illegal piece index: " + pieceIndex +
                    ", expected 0.." + (getPiecesTotal() - 1));
        }
    }

    /**
     * Mark a piece as skipped
     *
     * @since 1.7
     */
    public void skip(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            if (skipped == null) {
                skipped = new BitSet(getPiecesTotal());
            }
            skipped.set(pieceIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark a piece as not skipped
     *
     * @since 1.7
     */
    public void unskip(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        if (skipped != null) {
            lock.lock();
            try {
                skipped.clear(pieceIndex);
            } finally {
                lock.unlock();
            }
        }
    }
}
