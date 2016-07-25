package bt.protocol;

public final class NotInterested implements Message {

    private NotInterested() {
    }

    private static final NotInterested instance = new NotInterested();

    public static NotInterested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.NOT_INTERESTED_ID;
    }
}
