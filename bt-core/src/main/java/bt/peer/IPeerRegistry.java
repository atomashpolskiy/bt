package bt.peer;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.net.InetAddress;
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
     */
    void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer);

    /**
     * Remove all listeners for the given torrent.
     *
     * @since 1.0
     */
    void removePeerConsumers(Torrent torrent);
}
