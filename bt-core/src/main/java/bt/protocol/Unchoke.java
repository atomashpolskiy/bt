package bt.protocol;

public class Unchoke implements Message {

    private Unchoke() {
    }

    private static final Unchoke instance = new Unchoke();

    public static Unchoke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }
}
