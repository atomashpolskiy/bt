package bt;

/**
 * Generic Bt exception
 *
 * @since 1.0
 */
public class BtException extends RuntimeException {

    /**
     * @since 1.0
     */
    public BtException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @since 1.0
     */
    public BtException(String message) {
        super(message);
    }
}
