package bt.protocol;

public class Bitfield implements Message {

    private byte[] bitfield;

    Bitfield(byte[] bitfield) {
        this.bitfield = bitfield;
    }

    @Override
    public MessageType getType() {
        return MessageType.BITFIELD;
    }

    public byte[] getBitfield() {
        return bitfield;
    }
}
