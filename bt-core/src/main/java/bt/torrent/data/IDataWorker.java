package bt.torrent.data;

import bt.net.Peer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Data worker is responsible for processing
 * blocks and block requests, received from peers.
 *
 * It is explicitly marked as {@link Runnable}
 * and is usually launched in a dedicated thread,
 * which means that its' {@link Runnable#run()} method
 * should implement a loop.
 *
 * @since 1.0
 */
public interface IDataWorker extends Runnable {

    /**
     * Add a read block request.
     *
     * @param peer Requestor
     * @param pieceIndex Index of the requested piece (0-based)
     * @param offset Offset in piece to start reading from (0-based)
     * @param length Amount of bytes to read
     * @return true if data worker accepted the request
     * @since 1.0
     */
    CompletableFuture<BlockRead> addBlockRequest(Peer peer, int pieceIndex, int offset, int length);

    /**
     * Add a write block request.
     *
     * @param peer Peer, that the data has been received from
     * @param pieceIndex Index of the piece to write to (0-based)
     * @param offset Offset in piece to start writing to (0-based)
     * @param block Data
     * @return Write block request
     * @since 1.0
     */
    CompletableFuture<BlockWrite> addBlock(Peer peer, int pieceIndex, int offset, byte[] block);

    // TODO: Get rid of this, return CompletableFuture from addBlock instead
    /**
     * @param listener Callback to be invoked when some piece is completed and verified.
     * @since 1.0
     */
    void addVerifiedPieceListener(Consumer<Integer> listener);
}
