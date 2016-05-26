package bt.protocol;

public class Handshake implements Message {

    private byte[] infoHash;
    private byte[] peerId;

    Handshake(byte[] infoHash, byte[] peerId) {
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
