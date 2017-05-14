package bt.net.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

class BigIntegers {

    static byte[] toByteArray(BigInteger i) {
        byte[] ibytes = i.toByteArray();
        return Arrays.copyOfRange(ibytes, 1, ibytes.length);
    }

    static byte[] toByteArray(BigInteger i, int arrayLength) {
        byte[] ibytes = i.toByteArray();
        // leading byte is treated by BigInteger as signum
        if (ibytes.length > arrayLength + 1) {
            throw new IllegalArgumentException("Value truncation: value length (" +
                    ibytes.length + "), requested array length (" + arrayLength + "), expected 0.." + (ibytes.length - 1));
        }
        byte[] bytes = new byte[arrayLength];
        System.arraycopy(ibytes, 0, bytes, (bytes.length - ibytes.length), ibytes.length);
        return bytes;
    }

    static BigInteger fromBytes(ByteBuffer buffer, int byteCount) {
        byte[] bytes = new byte[byteCount + 1];
        buffer.get(bytes, 1, byteCount); // leading byte is treated by BigInteger as signum
        return new BigInteger(bytes);
    }
}
