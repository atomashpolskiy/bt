package bt.protocol;

public final class KeepAlive implements Message {

    private KeepAlive() {
    }

    private static final KeepAlive instance = new KeepAlive();

    public static KeepAlive instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        throw new UnsupportedOperationException();
    }
}
