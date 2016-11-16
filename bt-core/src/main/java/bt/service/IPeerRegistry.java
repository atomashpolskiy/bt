package bt.service;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.util.function.Consumer;

/**
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
     * Add a listener for new discovered peers
     * that are currently participating in this torrent.
     *
     * @since 1.0
     */
    void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer);

    /**
     * Remove all listeners for the given torrent.
     *
     * @since 1.0
     */
    void removePeerConsumers(Torrent torrent);
}
