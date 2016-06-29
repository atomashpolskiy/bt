package bt.protocol;

public class Interested implements Message {

    private Interested() {
    }

    private static final Interested instance = new Interested();

    public static Interested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }
}
