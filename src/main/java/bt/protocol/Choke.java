package bt.protocol;

public class Choke implements Message {

    private Choke() {
    }

    @Override
    public MessageType getType() {
        return MessageType.CHOKE;
    }

    private static final Choke instance = new Choke();

    static Choke instance() {
        return instance;
    }
}
