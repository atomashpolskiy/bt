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

import bt.protocol.BitOrder;
import bt.protocol.Protocols;

import java.util.BitSet;
import java.util.function.IntConsumer;

public class PeerBitfield extends Bitfield {
    /**
     * Create an empty bitfield for a peer
     *
     * @param piecesTotal the total number of pieces in torrent
     */
    public PeerBitfield(int piecesTotal) {
        super(piecesTotal);
    }

    /**
     * Creates bitfield based on a bitmask.
     * Used for creating peers' bitfields.
     *
     * @param value       Bitmask that describes status of all pieces.
     *                    If position i is set to 1, then piece with index i is complete and verified.
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.7
     */
    public PeerBitfield(byte[] value, BitOrder bitOrder, int piecesTotal) {
        super(piecesTotal, createBitmask(value, bitOrder, piecesTotal));
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

        BitSet ret = BitSet.valueOf(bytes);

        // clear any extra bits set above the number of bits we need.
        if (ret.length() > piecesTotal) {
            ret.clear(piecesTotal, ret.length());
        }
        return ret;
    }

    /**
     * Mark piece as complete and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.10
     */
    public boolean markPeerPieceVerified(int pieceIndex) {
        return checkAndMarkVerified(pieceIndex);
    }


    public void forEachVerifiedPiece(IntConsumer consumer) {
        lock.readLock().lock();
        try {
            this.bitmask.stream().forEach(consumer);
        } finally {
            lock.readLock().unlock();
        }
    }
}
