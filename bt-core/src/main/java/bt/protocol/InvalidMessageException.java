package bt.protocol;

public class InvalidMessageException extends RuntimeException {

    InvalidMessageException(String message) {
        super(message);
    }
}
