package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.Peer;

/**
 * @since 1.0
 */
public interface IPeerWorkerFactory {

    /**
     * Create a peer worker for a given peer.
     *
     * @since 1.0
     */
    PeerWorker createPeerWorker(Peer peer);

    /**
     * Create a torrent-aware peer worker for a given peer.
     *
     * @since 1.0
     */
    PeerWorker createPeerWorker(TorrentId torrentId, Peer peer);
}
