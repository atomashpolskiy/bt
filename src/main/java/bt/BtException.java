package bt;

public class BtException extends RuntimeException {

    public BtException(String message, Throwable cause) {
        super(message, cause);
    }

    public BtException(String message) {
        super(message);
    }
}
