package bt.protocol;

public class KeepAlive implements Message {

    private KeepAlive() {
    }

    @Override
    public MessageType getType() {
        return MessageType.KEEPALIVE;
    }

    private static final KeepAlive instance = new KeepAlive();

    public static KeepAlive instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + getType().name() + "]";
    }
}
