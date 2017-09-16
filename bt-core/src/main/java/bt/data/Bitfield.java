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
import bt.protocol.Protocols;

import java.util.Arrays;
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
     * Standard bittorrent bitfield, where n-th bit
     * (counting from high position to low)
     * indicates the availability of n-th piece.
     */
    private final byte[] value;

    /**
     * Total number of pieces in torrent.
     */
    private final int piecesTotal;

    /**
     * Number of pieces that have status {@link PieceStatus#COMPLETE_VERIFIED}.
     */
    private volatile int piecesComplete;

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
        this.chunks = Optional.of(chunks);
        this.piecesTotal = chunks.size();
        this.value = new byte[getBitmaskLength(piecesTotal)];
        this.piecesComplete = 0;
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
        this(new byte[getBitmaskLength(piecesTotal)], piecesTotal);
    }

    /**
     * Creates bitfield based on a bitmask.
     * Used for creating peers' bitfields.
     *
     * @param value Bitmask that describes status of all pieces.
     *              If position i is set to 1, then piece with index i is complete and verified.
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.0
     */
    public Bitfield(byte[] value, int piecesTotal) {

        int expectedBitmaskLength = getBitmaskLength(piecesTotal);
        if (value.length != expectedBitmaskLength) {
            throw new IllegalArgumentException("Invalid bitfield: total (" + piecesTotal +
                    "), bitmask length (" + value.length + "). Expected bitmask length: " + expectedBitmaskLength);
        }

        this.value = value;
        this.chunks = Optional.empty();
        this.piecesTotal = piecesTotal;
        this.piecesComplete = getPiecesComplete(value);
        this.lock = new ReentrantLock();
    }

    private static int getBitmaskLength(int piecesTotal) {
        return (int) Math.ceil(piecesTotal / 8d);
    }

    private static int getPiecesComplete(byte[] value) {
        return BitSet.valueOf(value).cardinality();
    }

    /**
     * @return Bitmask that describes status of all pieces.
     *         If position i is set to 1, then piece with index i
     *         is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.0
     */
    public byte[] getBitmask() {
        lock.lock();
        try {
            return Arrays.copyOf(value, value.length);
        } finally {
            lock.unlock();
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
        return piecesComplete;
    }

    /**
     * @return Number of pieces that have status different from {@link PieceStatus#COMPLETE_VERIFIED}.
     *         I.e. it's the same as {@link #getPiecesTotal()} - {@link #getPiecesComplete()}
     * @since 1.0
     */
    public int getPiecesRemaining() {
        return piecesTotal - piecesComplete;
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
            verified = Protocols.getBit(value, pieceIndex) == 1;
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
            Protocols.setBit(value, pieceIndex);
            piecesComplete = getPiecesComplete(value);
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
        if (pieceIndex < 0 || pieceIndex >= piecesTotal) {
            throw new BtException("Illegal piece index: " + pieceIndex);
        }
    }
}
