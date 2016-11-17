package bt.metainfo;

import bt.BtException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Object wrapper for binary torrent identifiers.
 *
 * @since 1.0
 */
public class TorrentId {

    private static final int TORRENT_ID_LENGTH = 20;

    /**
     * @return Length in bytes
     * @since 1.0
     */
    public static int length() {
        return TORRENT_ID_LENGTH;
    }

    /**
     * Create a TorrentId instance from a binary representation.
     *
     * @param bytes Binary representation of torrent's ID
     * @since 1.0
     */
    public static TorrentId fromBytes(byte[] bytes) {
        return new TorrentId(bytes);
    }

    private final byte[] torrentId;

    private TorrentId(byte[] torrentId) {
        Objects.requireNonNull(torrentId);
        if (torrentId.length != TORRENT_ID_LENGTH) {
            throw new BtException("Illegal torrent ID length: " + torrentId.length);
        }
        this.torrentId = torrentId;
    }

    /**
     * @return Binary representation of torrent's ID
     * @since 1.0
     */
    public byte[] getBytes() {
        return torrentId;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(torrentId);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null || !TorrentId.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        return (obj == this) || Arrays.equals(torrentId, ((TorrentId) obj).getBytes());
    }

    @Override
    public String toString() {
        return Arrays.toString(torrentId);
    }
}
