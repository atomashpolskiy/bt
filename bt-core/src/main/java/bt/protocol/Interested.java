package bt.protocol;

/**
 * @since 1.0
 */
public final class Interested implements Message {

    private Interested() {
    }

    private static final Interested instance = new Interested();

    /**
     * @since 1.0
     */
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
