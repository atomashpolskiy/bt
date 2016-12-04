package bt.bencoding;

/**
 * BEncoded document parse exception.
 *
 * @since 1.0
 */
public class BtParseException extends RuntimeException {

    private byte[] scannedContents;

    /**
     * @since 1.0
     */
    public BtParseException(String message, byte[] scannedContents, Throwable cause) {
        super(message, cause);
        this.scannedContents = scannedContents;
    }

    /**
     * @since 1.0
     */
    public BtParseException(String message, byte[] scannedContents) {
        super(message);
        this.scannedContents = scannedContents;
    }

    /**
     * Get the scanned portion of the source document.
     *
     * @return Scanned portion of the source document
     * @since 1.0
     */
    public byte[] getScannedContents() {
        return scannedContents;
    }
}
