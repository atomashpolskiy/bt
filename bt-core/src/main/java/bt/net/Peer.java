package bt.net;

import java.net.InetAddress;
import java.util.Optional;

/**
 * @since 1.0
 */
public interface Peer {

    /**
     * @return Peer Internet address.
     * @since 1.0
     */
    InetAddress getInetAddress();

    // TODO: probably need an additional property isReachable()
    // to distinguish between outbound and inbound connections
    // and to not send unreachable peers via PEX
    /**
     * @return Peer port.
     * @since 1.0
     */
    int getPort();

    /**
     * @return Optional peer ID.
     * @since 1.0
     */
    Optional<PeerId> getPeerId();
}
