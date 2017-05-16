package bt.net.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

class BigIntegers {

    static byte[] encodeUnsigned(BigInteger i, int byteCount) {
        if (byteCount < 1) {
            throw new IllegalArgumentException("Invalid number of bytes: " + byteCount);
        }
        byte[] bytes = i.toByteArray();
        if (bytes[0] == 0) {
            // first byte is prepended for a sign bit
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        if (bytes.length > byteCount) {
            throw new IllegalStateException("Value truncation");
        }
        if (bytes.length < byteCount) {
            byte[] bytesCopy = bytes;
            bytes = new byte[byteCount];
            System.arraycopy(bytesCopy, 0, bytes, (bytes.length - bytesCopy.length), bytesCopy.length);
        }
        return bytes;
    }

    static BigInteger decodeUnsigned(ByteBuffer buffer, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Invalid number of bytes: " + length);
        } else if (buffer.remaining() < length) {
            throw new IllegalStateException("Insufficient bytes in buffer: " + buffer.remaining() + ", requested: " + length);
        }
        // strip leading zeros
        byte b;
        int i = 0;
        while ((b = buffer.get()) == 0 && ++i < length)
            ;

        // all bytes were zeros
        if (i == length) {
            return BigInteger.ZERO;
        }

        int len = length - i;
        byte[] bytes = new byte[len];
        bytes[0] = b;
        buffer.get(bytes, 1, len - 1);
        return new BigInteger(1, bytes);
    }
}
