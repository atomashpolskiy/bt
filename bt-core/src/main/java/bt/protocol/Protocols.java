package bt.protocol;

import bt.BtException;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Utility class to use in binary protocol implementations.
 *
 * @since 1.0
 */
public class Protocols {

    /**
     * BitTorrent message prefix size in bytes.
     *
     * @since 1.0
     */
    public static final int MESSAGE_LENGTH_PREFIX_SIZE = 4;

    /**
     * BitTorrent message ID size in bytes.
     *
     * @since 1.0
     */
    public static final int MESSAGE_TYPE_SIZE = 1;

    /**
     * BitTorrent message prefix size in bytes.
     * Message prefix is a concatenation of message length prefix and message ID.
     *
     * @since 1.0
     */
    public static final int MESSAGE_PREFIX_SIZE = MESSAGE_LENGTH_PREFIX_SIZE + MESSAGE_TYPE_SIZE;

    //-------------------------//
    //--- utility functions ---//
    //-------------------------//

    /**
     * Get 4-bytes long binary representation of an {@link Integer}.
     *
     * @since 1.0
     */
    public static byte[] getIntBytes(int i) {
        return new byte[] {(byte) (i >> 24), (byte) (i >> 16), (byte) (i >> 8), (byte) i};
    }

    /**
     * Get 2-bytes long binary representation of a {@link Short}.
     *
     * @since 1.0
     */
    public static byte[] getShortBytes(int s) {
        return new byte[] {(byte) (s >> 8), (byte) s};
    }

    /**
     * Decode a binary integer representation into an {@link Integer}.
     *
     * @param bytes Arbitrary byte array.
     *              It's length must be at least {@code offset + 4}.
     * @param offset Offset in byte array to start decoding from (inclusive, 0-based)
     * @since 1.0
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
     * Decode a binary integer representation from a buffer into an {@link Integer}.
     *
     * @param buffer Buffer to read from.
     *               Decoding will be done starting with the index denoted by {@link Buffer#position()}
     * @return Decoded integer, or null if there are insufficient bytes in buffer
     *         (i.e. {@link Buffer#remaining()} < 4)
     * @since 1.0
     */
    public static Integer readInt(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES) {
            return null;
        }
        return buffer.getInt();
    }

    /**
     * Decode a binary short integer representation into a {@link Short}.
     *
     * @param bytes Arbitrary byte array.
     *              It's length must be at least {@code offset + 2}.
     * @param offset Offset in byte array to start decoding from (inclusive, 0-based)
     * @since 1.0
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

    /**
     * Decode a binary short integer representation from a buffer into a {@link Short}.
     *
     * @param buffer Buffer to read from.
     *               Decoding will be done starting with the index denoted by {@link Buffer#position()}
     * @return Decoded short integer, or null if there are insufficient bytes in buffer
     *         (i.e. {@link Buffer#remaining()} < 2)
     * @since 1.0
     */
    public static Integer readShort(ByteBuffer buffer) {

        if (buffer.remaining() < Integer.BYTES) {
            return null;
        }
        // TODO: switch to ByteBuffer.getShort
        return ((buffer.get() << 8) & 0xFF00) + (buffer.get() & 0x00FF);
    }

    /**
     * Convenience method to check if actual message length is the same as expected length.
     *
     * @throws InvalidMessageException if expectedLength != actualLength
     * @since 1.0
     */
    public static void verifyPayloadHasLength(Class<? extends Message> type, int expectedLength, int actualLength) {
        if (expectedLength != actualLength) {
            throw new InvalidMessageException("Unexpected payload length for " + type.getSimpleName() + ": " + actualLength +
                    " (expected " + expectedLength + ")");
        }
    }

    /**
     * Sets i-th bit in a bitmask.
     *
     * @param bytes Bitmask.
     * @param i Bit index (0-based)
     * @since 1.0
     */
    public static void setBit(byte[] bytes, int i) {

        int byteIndex = (int) (i / 8d);
        if (byteIndex >= bytes.length) {
            throw new BtException("bit index is too large: " + i);
        }

        int bitIndex = i % 8;
        int shift = (7 - bitIndex);
        int bitMask = 0b1 << shift;
        byte currentByte = bytes[byteIndex];
        bytes[byteIndex] = (byte) (currentByte | bitMask);
    }

    /**
     * Gets i-th bit in a bitmask.
     *
     * @param bytes Bitmask.
     * @param i Bit index (0-based)
     * @return 1 if bit is set, 0 otherwise
     * @since 1.0
     */
    public static int getBit(byte[] bytes, int i) {

        int byteIndex = (int) (i / 8d);
        if (byteIndex >= bytes.length) {
            throw new BtException("bit index is too large: " + i);
        }

        int bitIndex = i % 8;
        int shift = (7 - bitIndex);
        int bitMask = 0b1 << shift ;
        return (bytes[byteIndex] & bitMask) >> shift;
    }
}
