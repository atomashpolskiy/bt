package bt.protocol;

import bt.BtException;
import bt.Constants;

public class Handshake implements Message {

    private static final int UPPER_RESERVED_BOUND = 8 * 8 - 1;

    private byte[] reserved;
    private byte[] infoHash;
    private byte[] peerId;

    public Handshake(byte[] reserved, byte[] infoHash, byte[] peerId) throws InvalidMessageException {

        if (infoHash.length != Constants.INFO_HASH_LENGTH || peerId.length != Constants.PEER_ID_LENGTH) {
            throw new InvalidMessageException("Illegal arguments size: info hash (" +
                    infoHash.length + "), peer ID (" + peerId.length + ")");
        }

        this.reserved = reserved;
        this.infoHash = infoHash;
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

    public byte[] getInfoHash() {
        return infoHash;
    }

    public byte[] getPeerId() {
        return peerId;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }
}
