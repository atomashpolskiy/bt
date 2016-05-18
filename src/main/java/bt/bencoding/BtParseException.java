package bt.bencoding;

import bt.BtException;

public class BtParseException extends BtException {

    private byte[] scannedContents;

    public BtParseException(String message, byte[] scannedContents, Throwable cause) {
        super(message, cause);
        this.scannedContents = scannedContents;
    }

    public BtParseException(String message, byte[] scannedContents) {
        super(message);
        this.scannedContents = scannedContents;
    }

    public byte[] getScannedContents() {
        return scannedContents;
    }
}
