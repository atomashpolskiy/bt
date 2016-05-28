package bt.protocol;

public class Unchoke implements Message {

    private Unchoke() {
    }

    @Override
    public MessageType getType() {
        return MessageType.UNCHOKE;
    }

    private static final Unchoke instance = new Unchoke();

    static Unchoke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + getType().name() + "]";
    }
}
