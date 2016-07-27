package bt.protocol;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.net.PeerId;

public final class Handshake implements Message {

    private static final int UPPER_RESERVED_BOUND = 8 * 8 - 1;

    private byte[] reserved;
    private TorrentId torrentId;
    private PeerId peerId;

    public Handshake(byte[] reserved, TorrentId torrentId, PeerId peerId) throws InvalidMessageException {
        this.reserved = reserved;
        this.torrentId = torrentId;
        this.peerId = peerId;
    }

    public boolean isReservedBitSet(int bitIndex) {
        return Protocols.getBit(reserved, bitIndex) == 1;
    }

    public void setReservedBit(int bitIndex) {
        if (bitIndex < 0 || bitIndex > UPPER_RESERVED_BOUND) {
            throw new BtException("Illegal bit index: " + bitIndex +
                    ". Expected index in range [0.." + UPPER_RESERVED_BOUND + "]");
        }
        Protocols.setBit(reserved, bitIndex);
        // check range
    }

    public byte[] getReserved() {
        return reserved;
    }

    public TorrentId getTorrentId() {
        return torrentId;
    }

    public PeerId getPeerId() {
        return peerId;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        throw new UnsupportedOperationException();
    }
}
