package bt.torrent;

import bt.BtException;
import bt.data.DataStatus;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.protocol.Protocols;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * Status of torrent's data.
 *
 * Instances of this class are not thread-safe.
 * External synchronization should be used in case of concurrent access.
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

    /**
     * Creates "local" bitfield from a list of chunk descriptors.
     *
     * @param chunks List of torrent's chunk descriptors.
     * @since 1.0
     */
    public Bitfield(List<ChunkDescriptor> chunks) {

        int chunkCount = chunks.size();
        byte[] bitfield = new byte[getBitmaskLength(chunkCount)];
        int piecesComplete = 0;
        int bitfieldIndex = 0;
        while (chunkCount > 0) {
            int b = 0, offset = bitfieldIndex * 8;
            int k = chunkCount < 8? chunkCount : 8;
            for (int i = 0; i < k; i++) {
                ChunkDescriptor chunk = chunks.get(offset + i);
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
        return Arrays.copyOf(value, value.length);
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

        if (chunks.isPresent()) {
            ChunkDescriptor chunk = chunks.get().get(pieceIndex);
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
     * Signal that a piece has been completed and verified.
     * In case with "local" bitfield an additional check
     * of the corresponding chunk descriptor's status is performed.
     * If the chunk is not in {@link DataStatus#VERIFIED} status, an exception is thrown.
     *
     * @param pieceIndex Piece index (0-based)
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public void markComplete(int pieceIndex) {

        boolean completed;
        if (chunks.isPresent()) {
            ChunkDescriptor chunk = chunks.get().get(pieceIndex);
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
