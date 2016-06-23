package bt.protocol;

public class Choke implements Message {

    private Choke() {
    }

    @Override
    public MessageType getType() {
        return MessageType.CHOKE;
    }

    private static final Choke instance = new Choke();

    public static Choke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + getType().name() + "]";
    }
}
