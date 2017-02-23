package bt.net;

import bt.metainfo.TorrentId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the main API for getting connections to remote peers.
 *
 * @since 1.0
 */
public interface IPeerConnectionPool {

    /**
     * Add a listener for receiving peer events.
     *
     * @since 1.0
     */
    void addConnectionListener(PeerActivityListener listener);

    /**
     * @return Connection for a given peer, if exists; null otherwise
     * @since 1.0
     */
    PeerConnection getConnection(Peer peer);

    /**
     * Request to establish a connection with a remote peer for a given torrent ID.
     *
     * @return Future connection if it can be established
     * @since 1.0
     */
    CompletableFuture<Optional<PeerConnection>> requestConnection(TorrentId torrentId, Peer peer);
}
