package bt.net;

import bt.peer.ImmutablePeer;

import java.net.InetAddress;

/**
 * This class represents a peer past the discovery stage, that we are in the process of establishing a connection with
 * it, or have successfully connected to it.
 * <p>
 * The port field may be mutated for incoming connections, because on incoming connections, the remote listening port
 * is not known, unless the peer shares it with an extended handshake (BEP-0010). This is imported for PEx (BEP-0011)
 * because we cannot share a peer's IP/port if we do not know the remote port.
 * <p>
 * Because this class is mutated, it does not have a {@link #hashCode()} or {@link #equals(Object)} methods. For this
 * reason, should not be put in map, except for when the {@link System#identityHashCode(Object)} provides the required
 * behavior
 */
public class InetPeer {
    public static final int UNKNOWN_PORT = -1;

    private final InetAddress address;
    // may be mutated
    private volatile int port;

    public InetPeer(Peer peer) {
        this(peer.getInetAddress(), peer.getPort());
    }

    public InetPeer(InetAddress address) {
        this(address, UNKNOWN_PORT);
    }

    public InetPeer(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public InetAddress getInetAddress() {
        return address;
    }

    public void setPort(int newPort) {
        ImmutablePeer.checkPort(newPort);
        if (port != UNKNOWN_PORT && port != newPort) {
            throw new IllegalStateException("Port already set to: " + port + "." +
                    " Attempted to update to: " + newPort);
        }
        port = newPort;
    }

    /**
     * @return Peer's listening port or {@link InetPeer#UNKNOWN_PORT}, if it's not known yet
     * (e.g. when the connection is incoming and the remote side hasn't
     * yet communicated to us its' listening port via extended handshake)
     * @since 1.0
     */
    public int getPort() {
        return port;
    }

    /**
     * @return true, if the peer's listening port is not known yet
     * @see #getPort()
     * @since 1.9
     */
    public boolean isPortUnknown() {
        return port == UNKNOWN_PORT;
    }
}
