package bt.net.peer;

import bt.net.PeerId;

import java.net.InetAddress;
import java.util.Objects;

/**
 * This class represents the peer information about this client.
 *
 * This peer intentionally does not implement the Peer interface so that it is not accidentally put into HashMaps with
 * immutable peers because it will not work as intended. Also, it does not contain peer options, and it contains
 * a PeerId.
 */
public class LocalPeer {
    private final InetAddress address;
    private final int port;
    private final PeerId peerId;

    /**
     * Construct an object that represents the local peer
     *
     * @param address the address for remote peers to connect to us on
     * @param port    the port for remote peers to connect to us on
     * @param peerId  the peerId of our client
     */
    public LocalPeer(InetAddress address, int port, PeerId peerId) {
        this.address = address;
        this.port = port;
        this.peerId = Objects.requireNonNull(peerId);
    }

    /**
     * Get the address for remote peers to connect to us on
     *
     * @return the address for remote peers to connect to us on
     */
    public InetAddress getInetAddress() {
        return address;
    }

    /**
     * Get the port for remote peers to connect to us on
     *
     * @return the port for remote peers to connect to us on
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the peer id of the local peer. This will always be populated
     *
     * @return the id of the local peer
     */
    public PeerId getPeerId() {
        return peerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalPeer localPeer = (LocalPeer) o;
        return port == localPeer.port && com.google.common.base.Objects.equal(address, localPeer.address);
    }

    @Override
    public int hashCode() {
        return com.google.common.base.Objects.hashCode(address, port);
    }
}
