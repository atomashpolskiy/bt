package bt.peer;

import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.tracker.AnnounceKey;

import java.net.InetSocketAddress;

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
