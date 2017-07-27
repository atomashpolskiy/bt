package bt.peer;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.tracker.AnnounceKey;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

/**
 * Shared registry of all peers, known to the current runtime.
 *
 * @since 1.0
 */
public interface IPeerRegistry {

    /**
     * Returns local peer, that represents current runtime in the swarm.
     *
     * @return Local peer
     * @since 1.0
     */
    Peer getLocalPeer();

    /**
     * Get peer for an internet address
     * or create and return a new one with default options, if it does not exist.
     *
     * @since 1.2
     */
    Peer getPeerForAddress(InetSocketAddress address);

    /**
     * Add a listener for new discovered peers
     * that are currently participating in this torrent.
     *
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #addPeerConsumer(TorrentId, Consumer)}
     */
    void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer);

    /**
     * Add a listener for new discovered peers
     * that are currently participating in this torrent.
     *
     * @since 1.3
     */
    void addPeerConsumer(TorrentId torrentId, Consumer<Peer> consumer);

    /**
     * Remove all listeners for the given torrent.
     *
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #removePeerConsumers(TorrentId)}
     */
    void removePeerConsumers(Torrent torrent);

    /**
     * Remove all listeners for the given torrent.
     *
     * @since 1.3
     */
    void removePeerConsumers(TorrentId torrentId);

    /**
     * Add peer for a given torrent and notify all peer consumers.
     *
     * @since 1.3
     */
    void addPeer(TorrentId torrentId, Peer peer);

    /**
     * Register a new tracker peer source for a given torrent, based on the provided announce key.
     * Note that the new peer source will NOT be used, if the torrent is private (as in BEP-27).
     *
     * @since 1.3
     */
    void addPeerSource(TorrentId torrentId, AnnounceKey announceKey);
}
