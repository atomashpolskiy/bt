package bt.net;

import bt.BtException;

import java.util.Arrays;
import java.util.Objects;

public class PeerId {

    private static final int PEER_ID_LENGTH = 20;

    public static int length() {
        return PEER_ID_LENGTH;
    }

    public static PeerId fromBytes(byte[] bytes) {
        return new PeerId(bytes);
    }

    private final byte[] peerId;

    private PeerId(byte[] peerId) {
        Objects.requireNonNull(peerId);
        if (peerId.length != PEER_ID_LENGTH) {
            throw new BtException("Illegal peer ID length: " + peerId.length);
        }
        this.peerId = peerId;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(peerId, peerId.length);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(peerId);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null || !PeerId.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        return (obj == this) || Arrays.equals(peerId, ((PeerId) obj).getBytes());
    }

    @Override
    public String toString() {
        return Arrays.toString(peerId);
    }
}
