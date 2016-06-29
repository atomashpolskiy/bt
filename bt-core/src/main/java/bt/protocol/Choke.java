package bt.protocol;

public class Choke implements Message {

    private Choke() {
    }

    private static final Choke instance = new Choke();

    public static Choke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }
}
