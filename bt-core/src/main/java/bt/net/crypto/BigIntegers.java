package bt.net.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

class BigIntegers {

    static byte[] toByteArray(BigInteger i, int byteCount) {
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
        } else if (bytes.length < byteCount) {
            byte[] bytesCopy = bytes;
            bytes = new byte[byteCount];
            System.arraycopy(bytesCopy, 0, bytes, (bytes.length - bytesCopy.length), bytesCopy.length);
        }
        return bytes;
    }

    static BigInteger fromBytes(ByteBuffer buffer, int byteCount) {
        if (byteCount < 1) {
            throw new IllegalArgumentException("Invalid number of bytes: " + byteCount);
        } else if (buffer.remaining() < byteCount) {
            throw new IllegalStateException("Insufficient bytes in buffer: " + buffer.remaining() + ", requested: " + byteCount);
        }
        // strip leading zeros
        byte b;
        int i = 0;
        while ((b = buffer.get()) == 0 && ++i < byteCount)
            ;

        // all bytes were zeros
        if (i == byteCount) {
            return BigInteger.ZERO;
        }

        byte[] bytes;
        int len = byteCount - i;
        if (b < 0) {
            // high bit is set, need to prepend a leading zero, so that the number is treated as positive
            bytes = new byte[len + 1];
            bytes[1] = b;
            if (len > 1) {
                buffer.get(bytes, 2, len - 1);
            }
        } else {
            bytes = new byte[byteCount];
            bytes[0] = b;
            if (len > 1) {
                buffer.get(bytes, 1, len - 1);
            }
        }
        return new BigInteger(bytes);
    }
}
