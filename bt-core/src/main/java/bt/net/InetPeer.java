package bt.net;

import bt.BtException;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;

public class InetPeer implements Peer {

    private InetAddress inetAddress;
    private int port;
    private Optional<PeerId> peerId;
    private Origin origin;

    private final int hash;

    public InetPeer(InetAddress inetAddress, int port, Origin origin) {
        this(inetAddress, port, origin, null);
    }

    public InetPeer(InetAddress inetAddress, int port, Origin origin, PeerId peerId) {

        if (inetAddress == null || port < 0) {
            throw new BtException("Invalid arguments (address: <" + inetAddress + ":" + port + ">)");
        }

        int hash = ((inetAddress.hashCode() * 31) + port) * 17;
        if (peerId != null) {
            hash += peerId.hashCode();
        }
        this.hash = hash;

        this.inetAddress = inetAddress;
        this.port = port;
        this.origin = Objects.requireNonNull(origin, "Missing peer origin");
        this.peerId = Optional.ofNullable(peerId);
    }

    @Override
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Optional<PeerId> getPeerId() {
        return peerId;
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object object) {

        if (object == this) {
            return true;
        }

        if (object == null || !(object instanceof Peer)) {
            return false;
        }

        Peer that = (Peer) object;
        return (port == that.getPort())
                && inetAddress.equals(that.getInetAddress())
                && peerId.equals(that.getPeerId());
    }

    @Override
    public String toString() {
        String description = inetAddress.toString() + ":" + port;
        if (peerId.isPresent()) {
            description += " (ID: " + peerId + ")";
        }
        return description;
    }
}
