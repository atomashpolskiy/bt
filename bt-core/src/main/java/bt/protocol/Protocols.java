package bt.protocol;

public class Protocols {

    //-------------------------//
    //--- utility functions ---//
    //-------------------------//

    public static byte[] getIntBytes(int i) {
        return new byte[] {(byte) (i >> 24), (byte) (i >> 16), (byte) (i >> 8), (byte) i};
    }

    public static byte[] getShortBytes(int s) {
        return new byte[] {(byte) (s >> 8), (byte) s};
    }

    /**
     * {@code bytes.length} must be at least {@code offset + java.lang.Integer.BYTES}
     */
    public static int getInt(byte[] bytes, int offset) {

        if (bytes.length < offset + Integer.BYTES) {
            throw new ArrayIndexOutOfBoundsException("insufficient byte array length (length: " + bytes.length +
                    ", offset: " + offset + ")");
        }
        // intentionally do not check bytes.length,
        // just take the first 4 bytes (starting with the offset)
        return ((bytes[offset] << 24) & 0xFF000000) + ((bytes[offset + 1] << 16) & 0x00FF0000)
                + ((bytes[offset + 2] << 8) & 0x0000FF00) + (bytes[offset + 3] & 0x000000FF);
    }

    /**
     * {@code bytes.length} must be at least {@code offset + java.lang.Short.BYTES}
     */
    public static int getShort(byte[] bytes, int offset) {

        if (bytes.length < offset + Short.BYTES) {
            throw new ArrayIndexOutOfBoundsException("insufficient byte array length (length: " + bytes.length +
                    ", offset: " + offset + ")");
        }
        // intentionally do not check bytes.length,
        // just take the first 2 bytes (starting with the offset)
        return ((bytes[offset] << 8) & 0xFF00) + (bytes[offset + 1] & 0x00FF);
    }

    public static void verifyPayloadLength(Class<? extends Message> type, int expectedLength, int actualLength) {
        if (expectedLength != actualLength) {
            throw new InvalidMessageException("Unexpected payload length for " + type.getSimpleName() + ": " + actualLength +
                    " (expected " + expectedLength + ")");
        }
    }
}
