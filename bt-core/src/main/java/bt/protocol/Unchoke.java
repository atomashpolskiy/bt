package bt.protocol;

public final class Unchoke implements Message {

    private Unchoke() {
    }

    private static final Unchoke instance = new Unchoke();

    public static Unchoke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.UNCHOKE_ID;
    }
}
