package bt.net;

import bt.BtException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Convenient wrapper, that encapsulates a binary peer ID.
 *
 * @since 1.0
 */
public class PeerId {

    private static final int PEER_ID_LENGTH = 20;

    /**
     * @return Standrad peer ID length in BitTorrent.
     * @since 1.0
     */
    public static int length() {
        return PEER_ID_LENGTH;
    }

    /**
     * Deserialize a binary peer ID representation.
     *
     * @since 1.0
     */
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

    /**
     * @return Binary peer ID representation.
     */
    public byte[] getBytes() {
        return peerId;
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
