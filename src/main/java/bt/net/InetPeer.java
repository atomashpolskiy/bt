package bt.net;

import bt.BtException;

import java.net.InetAddress;
import java.util.Arrays;

public class InetPeer implements Peer {

    private InetAddress inetAddress;
    private int port;
    private byte[] peerId;

    private final int hash;

    public InetPeer(InetAddress inetAddress, int port) {
        this(inetAddress, port, null);
    }

    public InetPeer(InetAddress inetAddress, int port, byte[] peerId) {

        if (inetAddress == null || port < 0) {
            throw new BtException("Invalid arguments (" + inetAddress + ":" + port + ")");
        }

        hash = ((inetAddress.hashCode() * 31) + port) * 17 + Arrays.hashCode(peerId);

        this.inetAddress = inetAddress;
        this.port = port;
        this.peerId = peerId;
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
    public byte[] getPeerId() {
        return peerId == null? null : Arrays.copyOf(peerId, peerId.length);
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
                && Arrays.equals(peerId, that.getPeerId());
    }

    @Override
    public String toString() {
        String description = inetAddress.toString() + ":" + port;
        if (peerId != null) {
            description += " (ID: " + Arrays.toString(peerId) + ")";
        }
        return description;
    }
}
