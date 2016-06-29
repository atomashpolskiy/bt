package bt.protocol;

public class Bitfield implements Message {

    private byte[] bitfield;

    public Bitfield(byte[] bitfield) {
        this.bitfield = bitfield;
    }

    public byte[] getBitfield() {
        return bitfield;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] bitfield {" + bitfield.length + " bytes}";
    }
}
