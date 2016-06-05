package bt.protocol;

public class NotInterested implements Message {

    private NotInterested() {
    }

    @Override
    public MessageType getType() {
        return MessageType.NOT_INTERESTED;
    }

    private static final NotInterested instance = new NotInterested();

    public static NotInterested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + getType().name() + "]";
    }
}
