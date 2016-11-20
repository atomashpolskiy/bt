package bt.torrent.data;

import bt.net.Peer;

import java.util.Optional;

/**
 * Write block command.
 *
 * If {@link #isRejected()} returns true,
 * this means that the request was not accepted by the data worker.
 * If {@link #getError()} is not empty,
 * this means that an exception happened during the request processing.
 *
 * @since 1.0
 */
public class BlockWrite {

    /**
     * @since 1.0
     */
    static BlockWrite complete(Peer peer, int pieceIndex, int offset, byte[] block) {
        return new BlockWrite(peer, null, false, pieceIndex, offset, block);
    }

    /**
     * @since 1.0
     */
    static BlockWrite rejected(Peer peer, int pieceIndex, int offset, byte[] block) {
        return new BlockWrite(peer, null, true, pieceIndex, offset, block);
    }

    /**
     * @since 1.0
     */
    static BlockWrite exceptional(Peer peer, Throwable error, int pieceIndex, int offset, byte[] block) {
        return new BlockWrite(peer, error, false, pieceIndex, offset, block);
    }

    private Peer peer;
    private int pieceIndex;
    private int offset;
    private byte[] block;

    private boolean rejected;
    private Optional<Throwable> error;

    private BlockWrite(Peer peer, Throwable error, boolean rejected, int pieceIndex, int offset, byte[] block) {
        this.peer = peer;
        this.error = Optional.ofNullable(error);
        this.rejected = rejected;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = block;
    }

    /**
     * @return Sending peer
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @return true if the request was not accepted by the data worker
     * @since 1.0
     */
    public boolean isRejected() {
        return rejected;
    }

    /**
     * @return Index of the piece being requested
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @return Offset in a piece to write the block to
     * @since 1.0
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return Block of data
     * @since 1.0
     */
    public byte[] getBlock() {
        return block;
    }

    /**
     * @return {@link Optional#empty()} if processing of the request completed normally,
     *         or exception otherwise.
     * @since 1.0
     */
    public Optional<Throwable> getError() {
        return error;
    }
}
