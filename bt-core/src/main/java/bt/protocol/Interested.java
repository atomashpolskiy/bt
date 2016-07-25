package bt.protocol;

public final class Interested implements Message {

    private Interested() {
    }

    private static final Interested instance = new Interested();

    public static Interested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.INTERESTED_ID;
    }
}
