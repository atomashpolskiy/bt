package bt.torrent.messaging;

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
    IPeerWorker createPeerWorker(Peer peer);
}
