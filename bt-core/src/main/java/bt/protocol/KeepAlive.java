package bt.protocol;

public class KeepAlive implements Message {

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
}
