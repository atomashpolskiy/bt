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

package bt.data;

import bt.BtException;
import bt.protocol.BitOrder;
import bt.protocol.Protocols;

import java.util.BitSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Status of torrent's data.
 * <p>
 * Instances of this class are thread-safe.
 *
 * @since 1.0
 */
public abstract class Bitfield {
    /**
     * Bitmask indicating availability of pieces.
     * If the n-th bit is set, then the n-th piece is complete and verified.
     */
    protected final BitSet bitmask;

    /**
     * Total number of pieces in torrent.
     */
    protected final int piecesTotal;

    protected final ReadWriteLock lock;

    /**
     * Creates empty bitfield.
     * Useful when peer does not communicate its' bitfield (e.g. when he has no data).
     *
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.0
     */
    protected Bitfield(int piecesTotal) {
        this(piecesTotal, new BitSet(piecesTotal));
    }

    /**
     * Creates a bitfield for with the bitset initially set to the passed in BitSet
     *
     * @param piecesTotal Total number of pieces in torrent
     * @param bitSet      the initial values of the bitfield
     */
    protected Bitfield(int piecesTotal, BitSet bitSet) {
        this.piecesTotal = piecesTotal;
        this.bitmask = bitSet;
        this.lock = new ReentrantReadWriteLock();
    }


    static int getBitmaskLength(int piecesTotal) {
        return (piecesTotal + 7) / 8;
    }

    /**
     * @return Bitmask that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is complete and verified.
     * @since 1.7
     */
    public BitSet getBitmask() {
        lock.readLock().lock();
        try {
            return Protocols.copyOf(bitmask);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @param bitOrder Order of bits to use to create the byte array
     * @return Bitmask that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is complete and verified.
     * @since 1.7
     */
    public byte[] toByteArray(BitOrder bitOrder) {
        byte[] bytes;
        boolean truncated = false;

        lock.readLock().lock();
        try {
            bytes = bitmask.toByteArray();
            truncated = (bitmask.length() < piecesTotal);
        } finally {
            lock.readLock().unlock();
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
     * @return Number of pieces that are complete and verified
     * @since 1.0
     */
    public int getPiecesComplete() {
        lock.readLock().lock();
        try {
            return bitmask.cardinality();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return Number of pieces that are not both completed and verified
     * @since 1.7
     */
    public int getPiecesIncomplete() {
        lock.readLock().lock();
        try {
            return getPiecesTotal() - bitmask.cardinality();
        } finally {
            lock.readLock().unlock();
        }
    }

    protected boolean isPieceVerified(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        boolean verified;
        lock.readLock().lock();
        try {
            verified = this.bitmask.get(pieceIndex);
        } finally {
            lock.readLock().unlock();
        }
        return verified;
    }

    /**
     * Marks the piece as verified and returns whether it was verified before it was marked as verified. Thread safe.
     *
     * @param pieceIndex the index of the piece to check and mark verified
     * @return true iff the piece was marked verified for the first time
     */
    protected boolean checkAndMarkVerified(int pieceIndex) {
        // write lock is required because this may modify the bitset and upgrading from read to write lock
        // is not supported
        lock.writeLock().lock();
        try {
            if (isVerified(pieceIndex)) {
                return false;
            } else {
                markVerified(pieceIndex);
                return true;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut method to find out if the piece has been downloaded.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded
     * @since 1.1
     */
    public boolean isComplete(int pieceIndex) {
        return isPieceVerified(pieceIndex);
    }

    /**
     * Shortcut method to find out if the piece has been downloaded and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded and verified
     * @since 1.1
     */
    public boolean isVerified(int pieceIndex) {
        return isPieceVerified(pieceIndex);
    }

    /**
     * Mark piece as complete and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    protected void markVerified(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.writeLock().lock();
        try {
            bitmask.set(pieceIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected void validatePieceIndex(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= getPiecesTotal()) {
            throw new BtException("Illegal piece index: " + pieceIndex +
                    ", expected 0.." + (getPiecesTotal() - 1));
        }
    }
}
