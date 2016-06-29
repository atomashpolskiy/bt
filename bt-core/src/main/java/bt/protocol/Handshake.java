package bt.protocol;

import bt.Constants;

public class Handshake implements Message {

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
