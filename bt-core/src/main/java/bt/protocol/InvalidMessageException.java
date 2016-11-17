package bt.protocol;

/**
 * Generic exception, that indicates that a message is invalid.
 * Usually thrown by message constructors.
 *
 * @since 1.0
 */
public class InvalidMessageException extends RuntimeException {

    /**
     * @since 1.0
     */
    public InvalidMessageException(String message) {
        super(message);
    }
}
