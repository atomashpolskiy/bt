package bt.torrent;

import bt.BtException;
import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.protocol.Protocols;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

public class Bitfield {

    // TODO: use EMPTY and PARTIAL instead of INCOMPLETE
    public enum PieceStatus {
        /*EMPTY, PARTIAL,*/INCOMPLETE, COMPLETE, COMPLETE_VERIFIED
    }

    /**
     * Standard bittorrent bitfield, where n-th bit
     * (counting from high position to low)
     * indicates the availability of n-th piece.
     */
    private final byte[] value;
    private final int piecesTotal;

    private volatile int piecesComplete;

    // can be absent for peer's bitfield
    private final Optional<List<IChunkDescriptor>> chunks;

    public Bitfield(List<IChunkDescriptor> chunks) {

        int chunkCount = chunks.size();
        byte[] bitfield = new byte[getBitmaskLength(chunkCount)];
        int piecesComplete = 0;
        int bitfieldIndex = 0;
        while (chunkCount > 0) {
            int b = 0, offset = bitfieldIndex * 8;
            int k = chunkCount < 8? chunkCount : 8;
            for (int i = 0; i < k; i++) {
                IChunkDescriptor chunk = chunks.get(offset + i);
                if (chunk.getStatus() == DataStatus.VERIFIED) {
                    b += 0b1 << (7 - i);
                    piecesComplete++;
                }
            }
            bitfield[bitfieldIndex] = (byte) b;
            bitfieldIndex++;
            chunkCount -= 8;
        }

        this.value = bitfield;
        this.chunks = Optional.of(chunks);
        this.piecesTotal = chunks.size();
        this.piecesComplete = piecesComplete;
    }

    public Bitfield(int piecesTotal) {
        this(new byte[getBitmaskLength(piecesTotal)], piecesTotal);
    }

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
    }

    private static int getBitmaskLength(int piecesTotal) {
        return (int) Math.ceil(piecesTotal / 8d);
    }

    private static int getPiecesComplete(byte[] value) {
        return BitSet.valueOf(value).cardinality();
    }

    public byte[] getBitmask() {
        return Arrays.copyOf(value, value.length);
    }

    public int getPiecesTotal() {
        return piecesTotal;
    }

    public int getPiecesComplete() {
        return piecesComplete;
    }

    public int getPiecesRemaining() {
        return piecesTotal - piecesComplete;
    }

    public PieceStatus getPieceStatus(int pieceIndex) {

        validatePieceIndex(pieceIndex);

        PieceStatus status;

        if (chunks.isPresent()) {
            IChunkDescriptor chunk = chunks.get().get(pieceIndex);
            switch (chunk.getStatus()) {
                case VERIFIED: {
                    markComplete(pieceIndex);
                    status = PieceStatus.COMPLETE_VERIFIED;
                    break;
                }
                case COMPLETE: {
                    status = PieceStatus.COMPLETE;
                    break;
                }
                default: {
                    status = PieceStatus.INCOMPLETE;
                }
            }
        } else if (Protocols.getBit(value, pieceIndex) == 1) {
            status = PieceStatus.COMPLETE_VERIFIED;
        } else {
            status = PieceStatus.INCOMPLETE;
        }
        return status;
    }

    public void markComplete(int pieceIndex) {

        boolean completed;
        if (chunks.isPresent()) {
            IChunkDescriptor chunk = chunks.get().get(pieceIndex);
            completed = (chunk.getStatus() == DataStatus.VERIFIED);
        } else {
            completed = true;
        }

        if (completed) {
            Protocols.setBit(value, pieceIndex);
            piecesComplete = getPiecesComplete(value);
        } else {
            throw new IllegalStateException("Can't mark piece as completed: " + pieceIndex);
        }
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= piecesTotal) {
            throw new BtException("Illegal piece index: " + pieceIndex);
        }
    }
}
