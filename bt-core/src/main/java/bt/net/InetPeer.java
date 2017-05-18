package bt.net;

import bt.peer.PeerOptions;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * @since 1.0
 */
public class InetPeer implements Peer {

    private InetSocketAddress address;
    private Optional<PeerId> peerId;

    private final PeerOptions options;

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port) {
        this(inetAddress, port, null, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.2
     */
    public InetPeer(InetSocketAddress address) {
        this(address, null, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port, PeerOptions options) {
        this(inetAddress, port, null, options);
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetSocketAddress address, PeerOptions options) {
        this(address, null, options);
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port, PeerId peerId) {
        this(inetAddress, port, peerId, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.2
     */
    public InetPeer(InetSocketAddress address, PeerId peerId) {
        this(address, peerId, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port, PeerId peerId, PeerOptions options) {
        this(createAddress(inetAddress, port), peerId, options);
    }

    /**
     * @since 1.2
     */
    public InetPeer(InetSocketAddress address, PeerId peerId, PeerOptions options) {
        this.address = address;
        this.peerId = Optional.ofNullable(peerId);
        this.options = options;
    }

    private static InetSocketAddress createAddress(InetAddress inetAddress, int port) {
        if (inetAddress == null || port < 0) {
            throw new IllegalArgumentException("Invalid arguments (address: <" + inetAddress + ":" + port + ">)");
        }
        return new InetSocketAddress(inetAddress, port);
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        return address;
    }

    @Override
    public InetAddress getInetAddress() {
        return address.getAddress();
    }

    // TODO: probably need an additional property isReachable()
    // to distinguish between outbound and inbound connections
    // and to not send unreachable peers via PEX
    @Override
    public int getPort() {
        return address.getPort();
    }

    @Override
    public Optional<PeerId> getPeerId() {
        return peerId;
    }

    @Override
    public PeerOptions getOptions() {
        return options;
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    /**
     * Compares peers by address, regardless of the particular classes.
     */
    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || !Peer.class.isAssignableFrom(object.getClass())) {
            return false;
        }

        Peer that = (Peer) object;
        return address.equals(that.getInetSocketAddress());
    }

    @Override
    public String toString() {
        return address.toString();
    }
}
