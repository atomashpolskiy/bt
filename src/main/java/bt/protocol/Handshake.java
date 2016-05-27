package bt.protocol;

import bt.Constants;

public class Handshake implements Message {

    private byte[] infoHash;
    private byte[] peerId;

    Handshake(byte[] infoHash, byte[] peerId) throws InvalidMessageException {

        if (infoHash.length != Constants.INFO_HASH_LENGTH || peerId.length != Constants.PEER_ID_LENGTH) {
            throw new InvalidMessageException("Illegal arguments size: info hash (" +
                    infoHash.length + "), peer ID (" + peerId.length + ")");
        }
        this.infoHash = infoHash;
        this.peerId = peerId;
    }

    @Override
    public MessageType getType() {
        return MessageType.HANDSHAKE;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public byte[] getPeerId() {
        return peerId;
    }
}
