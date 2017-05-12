package bt.net.crypto;

import bt.metainfo.TorrentId;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StreamCipher {

    private final Key incomingKey;
    private final Key outgoingKey;

    public static StreamCipher forInitiator(BigInteger S, TorrentId torrentId) {
        return new StreamCipher(S, torrentId, true);
    }

    public static StreamCipher forReceiver(BigInteger S, TorrentId torrentId) {
        return new StreamCipher(S, torrentId, false);
    }

    private StreamCipher(BigInteger S, TorrentId torrentId, boolean initiator) {
        Key outgoingKey = getInitiatorEncryptionKey(S.toByteArray(), torrentId.getBytes());
        Key incomingKey = getReceiverEncryptionKey(S.toByteArray(), torrentId.getBytes());
        this.outgoingKey = initiator ? outgoingKey : incomingKey;
        this.incomingKey = initiator ? incomingKey : outgoingKey;
    }

    public OutputStream encryptOugoingChannel(WritableByteChannel channel) {
        return encryptChannel(channel, outgoingKey);
    }

    public InputStream encryptIncomingChannel(ReadableByteChannel channel) {
        return encryptChannel(channel, incomingKey);
    }

    private Key getInitiatorEncryptionKey(byte[] S, byte[] SKEY) {
        return getEncryptionKey("keyA", S, SKEY);
    }

    private Key getReceiverEncryptionKey(byte[] S, byte[] SKEY) {
        return getEncryptionKey("keyB", S, SKEY);
    }

    private Key getEncryptionKey(String s, byte[] S, byte[] SKEY) {
        try {
            MessageDigest digest = getDigest("SHA-1");
            digest.update(s.getBytes("ASCII"));
            digest.update(S);
            digest.update(SKEY);
            return new SecretKeySpec(digest.digest(), "ARCFOUR");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private OutputStream encryptChannel(WritableByteChannel channel, Key key) {
        String transformation = "ARCFOUR";
        Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, transformation, key);
        return new CipherOutputStream(Channels.newOutputStream(channel), cipher);
    }

    private InputStream encryptChannel(ReadableByteChannel channel, Key key) {
        String transformation = "ARCFOUR";
        Cipher cipher = createCipher(Cipher.DECRYPT_MODE, transformation, key);
        return new CipherInputStream(Channels.newInputStream(channel), cipher);
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
