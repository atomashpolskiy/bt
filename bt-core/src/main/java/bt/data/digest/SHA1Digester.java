package bt.data.digest;

public class SHA1Digester extends JavaSecurityDigester {

    public static Digester rolling(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Invalid step: " + step);
        }
        return new SHA1Digester(step);
    }

    private SHA1Digester(int step) {
        super("SHA-1", step);
    }
}
