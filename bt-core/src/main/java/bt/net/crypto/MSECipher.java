package bt.net.crypto;

import bt.metainfo.TorrentId;
import bt.net.BigIntegers;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * RC4-drop1024 stream cipher, used in Message Stream Encryption protocol.
 *
 * Ciphers that are returned by {@link #getEncryptionCipher()} and {@link #getDecryptionCipher()}
 * will be different, depending on which of the factory methods was used to build an instance of this class:
 * - connection initiating side should use {@link #forInitiator(BigInteger, TorrentId)} factory method
 * - receiver of connection request should use {@link #forReceiver(BigInteger, TorrentId)} factory method
 *
 * @since 1.2
 */
public class MSECipher {

    private static final String transformation = "ARCFOUR/ECB/NoPadding";

    private final Cipher incomingCipher;
    private final Cipher outgoingCipher;

    /**
     * Create MSE cipher for connection initiator
     *
     * @param S Shared secret
     * @param torrentId Torrent id
     * @return MSE cipher configured for use by connection initiator
     * @since 1.2
     */
    public static MSECipher forInitiator(BigInteger S, TorrentId torrentId) {
        return new MSECipher(S, torrentId, true);
    }

    /**
     * Create MSE cipher for receiver of the connection request
     *
     * @param S Shared secret
     * @param torrentId Torrent id
     * @return MSE cipher configured for use by receiver of the connection request
     * @since 1.2
     */
    public static MSECipher forReceiver(BigInteger S, TorrentId torrentId) {
        return new MSECipher(S, torrentId, false);
    }

    private MSECipher(BigInteger S, TorrentId torrentId, boolean initiator) {
        byte[] Sbytes = BigIntegers.encodeUnsigned(S, MSEKeyPairGenerator.PUBLIC_KEY_BYTES);
        Key initiatorKey = getInitiatorEncryptionKey(Sbytes, torrentId.getBytes());
        Key receiverKey = getReceiverEncryptionKey(Sbytes, torrentId.getBytes());
        Key outgoingKey = initiator ? initiatorKey : receiverKey;
        Key incomingKey = initiator ? receiverKey : initiatorKey;
        this.incomingCipher = createCipher(Cipher.DECRYPT_MODE, transformation, incomingKey);
        this.outgoingCipher = createCipher(Cipher.ENCRYPT_MODE, transformation, outgoingKey);
    }

    /**
     * @return Cipher for encrypting outgoing data
     * @since 1.2
     */
    public Cipher getEncryptionCipher() {
        return outgoingCipher;
    }

    /**
     * @return Cipher for decrypting incoming data
     * @since 1.2
     */
    public Cipher getDecryptionCipher() {
        return incomingCipher;
    }

    private Key getInitiatorEncryptionKey(byte[] S, byte[] SKEY) {
        return getEncryptionKey("keyA", S, SKEY);
    }

    private Key getReceiverEncryptionKey(byte[] S, byte[] SKEY) {
        return getEncryptionKey("keyB", S, SKEY);
    }

    private Key getEncryptionKey(String s, byte[] S, byte[] SKEY) {
        MessageDigest digest = getDigest("SHA-1");
        digest.update(s.getBytes(Charset.forName("ASCII")));
        digest.update(S);
        digest.update(SKEY);
        return new SecretKeySpec(digest.digest(), "ARCFOUR");
    }

    private MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Cipher createCipher(int mode, String transformation, Key key) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(transformation);
            cipher.init(mode, key);
            cipher.update(new byte[1024]); // discard first 1024 bytes
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cipher;
    }
}
