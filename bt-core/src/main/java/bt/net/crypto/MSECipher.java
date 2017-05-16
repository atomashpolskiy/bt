package bt.net.crypto;

import bt.metainfo.TorrentId;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MSECipher {

    private static final String transformation = "ARCFOUR/ECB/NoPadding";

    private final Cipher incomingCipher;
    private final Cipher outgoingCipher;

    public static MSECipher forInitiator(byte[] S, TorrentId torrentId) {
        return new MSECipher(S, torrentId, true);
    }

    public static MSECipher forReceiver(byte[] S, TorrentId torrentId) {
        return new MSECipher(S, torrentId, false);
    }

    private MSECipher(byte[] S, TorrentId torrentId, boolean initiator) {
        Key initiatorKey = getInitiatorEncryptionKey(S, torrentId.getBytes());
        Key receiverKey = getReceiverEncryptionKey(S, torrentId.getBytes());
        Key outgoingKey = initiator ? initiatorKey : receiverKey;
        Key incomingKey = initiator ? receiverKey : initiatorKey;
        this.incomingCipher = createCipher(Cipher.DECRYPT_MODE, transformation, incomingKey);
        this.outgoingCipher = createCipher(Cipher.ENCRYPT_MODE, transformation, outgoingKey);
    }

    public Cipher getEncryptionCipher() {
        return outgoingCipher;
    }

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
