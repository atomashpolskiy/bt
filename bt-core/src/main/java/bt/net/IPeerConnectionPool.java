package bt.net;

import bt.metainfo.TorrentId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Provides the main API for getting connections to remote peers.
 *
 * @since 1.0
 */
public interface IPeerConnectionPool {

    /**
     * @return Connection for a given peer, if exists; null otherwise
     * @since 1.0
     */
    PeerConnection getConnection(Peer peer);

    /**
     * Visit connections for a given torrent ID.
     *
     * @since 1.5
     */
    void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor);

    /**
     * Request to establish a connection with a remote peer for a given torrent ID.
     *
     * @return Future connection if it can be established
     * @since 1.0
     */
    CompletableFuture<Optional<PeerConnection>> requestConnection(TorrentId torrentId, Peer peer);
}
