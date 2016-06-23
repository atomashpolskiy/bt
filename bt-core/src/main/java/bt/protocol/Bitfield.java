package bt.protocol;

public class Bitfield implements Message {

    private byte[] bitfield;

    public Bitfield(byte[] bitfield) {
        this.bitfield = bitfield;
    }

    @Override
    public MessageType getType() {
        return MessageType.BITFIELD;
    }

    public byte[] getBitfield() {
        return bitfield;
    }

    @Override
    public String toString() {
        return "[" + getType().name() + "] bitfield {" + bitfield.length + " bytes}";
    }
}
