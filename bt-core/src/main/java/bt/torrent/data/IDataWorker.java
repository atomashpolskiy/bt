package bt.torrent.data;

import bt.net.Peer;

import java.util.concurrent.CompletableFuture;

/**
 * Data worker is responsible for processing blocks and block requests, received from peers.
 *
 * @since 1.0
 */
public interface IDataWorker {

    /**
     * Add a read block request.
     *
     * @param peer Requestor
     * @param pieceIndex Index of the requested piece (0-based)
     * @param offset Offset in piece to start reading from (0-based)
     * @param length Amount of bytes to read
     * @return Future; rejected requests are returned immediately (see {@link BlockRead#isRejected()})
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
     * @return Future; rejected requests are returned immediately (see {@link BlockWrite#isRejected()})
     * @since 1.0
     */
    CompletableFuture<BlockWrite> addBlock(Peer peer, int pieceIndex, int offset, byte[] block);
}
