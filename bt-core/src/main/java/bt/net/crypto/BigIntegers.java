package bt.net.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;

class BigIntegers {

    static byte[] toByteArray(BigInteger i, int bitlen) {
        return getBytes(i, bitlen);
    }

    // copied from com.sun.org.apache.xml.internal.security.utils.Base64
    /**
     * Returns a byte-array representation of a <code>{@link BigInteger}<code>.
     * No sign-bit is output.
     *
     * <b>N.B.:</B> <code>{@link BigInteger}<code>'s toByteArray
     * returns eventually longer arrays because of the leading sign-bit.
     *
     * @param big <code>BigInteger<code> to be converted
     * @param bitlen <code>int<code> the desired length in bits of the representation
     * @return a byte array with <code>bitlen</code> bits of <code>big</code>
     */
    private static byte[] getBytes(BigInteger big, int bitlen) {

        //round bitlen
        bitlen = ((bitlen + 7) >> 3) << 3;

        if (bitlen < big.bitLength()) {
            throw new IllegalArgumentException("Value truncation");
        }

        byte[] bigBytes = big.toByteArray();

        if (((big.bitLength() % 8) != 0)
            && (((big.bitLength() / 8) + 1) == (bitlen / 8))) {
            return bigBytes;
        }

        // some copying needed
        int startSrc = 0;    // no need to skip anything
        int bigLen = bigBytes.length;    //valid length of the string

        if ((big.bitLength() % 8) == 0) {    // correct values
            startSrc = 1;    // skip sign bit

            bigLen--;    // valid length of the string
        }

        int startDst = bitlen / 8 - bigLen;    //pad with leading nulls
        byte[] resizedBytes = new byte[bitlen / 8];

        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, bigLen);

        return resizedBytes;
    }

    // TODO: figure out how to correctly use new BigInteger(byte[])
    static BigInteger fromBytes(ByteBuffer buffer, int byteCount) {
        byte[] bytes = new byte[byteCount];
        buffer.get(bytes);
        return new BigInteger(bytesToHex(bytes), 16);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
