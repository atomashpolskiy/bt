package bt.service;

import bt.net.Peer;

import java.util.Collection;

/**
 * @since 1.0
 */
public interface PeerSource {

    /**
     * Ask to update the list of peers (usually from an external source).
     * Implementations may choose to refuse to perform an update under certain conditions,
     * e.g. when insufficient time has passed since the last update.
     *
     * @return true if the list of peers has been updated; false otherwise
     * @since 1.0
     */
    boolean update();

    /**
     * Get the list of peers.
     * Implementations should return empty list before {@link #update} has been called for the first time.
     *
     * @return List of peers
     * @since 1.0
     */
    Collection<Peer> getPeers();
}
