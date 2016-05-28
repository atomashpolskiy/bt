package bt.protocol;

public class Interested implements Message {

    private Interested() {
    }

    @Override
    public MessageType getType() {
        return MessageType.INTERESTED;
    }

    private static final Interested instance = new Interested();

    static Interested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + getType().name() + "]";
    }
}
