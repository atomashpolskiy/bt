package bt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CryptoUtil {

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
