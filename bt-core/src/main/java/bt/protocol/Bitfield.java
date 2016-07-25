package bt.protocol;

public final class Bitfield implements Message {

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

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.BITFIELD_ID;
    }
}
