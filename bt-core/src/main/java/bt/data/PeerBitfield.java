package bt.data;

import bt.protocol.BitOrder;
import bt.protocol.Protocols;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public class PeerBitfield extends Bitfield {
    private final AtomicInteger piecesLeft;

    /**
     * Create an empty bitfield for a peer
     *
     * @param piecesTotal the total number of pieces in torrent
     */
    public PeerBitfield(int piecesTotal) {
        super(piecesTotal);
        this.piecesLeft = new AtomicInteger(piecesTotal);
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
        this.piecesLeft = new AtomicInteger(super.getPiecesIncomplete());
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
        boolean newlyVerified = checkAndMarkVerified(pieceIndex);
        if (newlyVerified) {
            piecesLeft.decrementAndGet();
        }
        return newlyVerified;
    }


    public void forEachVerifiedPiece(IntConsumer consumer) {
        lock.readLock().lock();
        try {
            this.bitmask.stream().forEach(consumer);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int getPiecesIncomplete() {
        return piecesLeft.get();
    }
}
