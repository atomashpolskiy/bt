package bt.service;

import bt.BtException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This utility class provides cryptographic functions.
 *
 * @since 1.0
 */
public class CryptoUtil {

    /**
     * Calculate SHA-1 digest of a byte array.
     *
     * @since 1.0
     */
    public static byte[] getSha1Digest(byte[] bytes) {
        MessageDigest cryptor;
        try {
            cryptor = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new BtException("Unexpected error", e);
        }
        return cryptor.digest(bytes);
    }
}
