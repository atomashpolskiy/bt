package bt.net;

import bt.peer.PeerOptions;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Represents a peer, accessible on the Internet.
 *
 * @since 1.0
 */
public interface Peer {

    /**
     * @since 1.2
     */
    InetSocketAddress getInetSocketAddress();

    /**
     * @return Peer Internet address.
     * @since 1.0
     */
    InetAddress getInetAddress();

    /**
     * @return Peer port.
     * @since 1.0
     */
    int getPort();

    /**
     * @return Optional peer ID
     * @since 1.0
     */
    Optional<PeerId> getPeerId();

    /**
     * @return Peer options and preferences
     * @since 1.2
     */
    PeerOptions getOptions();
}
