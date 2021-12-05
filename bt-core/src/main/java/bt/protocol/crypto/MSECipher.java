/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.protocol.crypto;

import bt.BtException;
import bt.metainfo.TorrentId;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * RC4-drop1024 stream cipher, used in Message Stream Encryption protocol.
 *
 * Ciphers that are returned by {@link #getEncryptionCipher()} and {@link #getDecryptionCipher()}
 * will be different, depending on which of the factory methods was used to build an instance of this class:
 * - connection initiating side should use {@link #forInitiator(byte[], TorrentId)} factory method
 * - receiver of connection request should use {@link #forReceiver(byte[], TorrentId)} factory method
 *
 * @since 1.2
 */
public class MSECipher {

    private static final String transformation = "ARCFOUR/ECB/NoPadding";

    private final Cipher incomingCipher;
    private final Cipher outgoingCipher;

    /**
     * @throws BtException if the check can't be performed,
     *                     e.g. when the MSE-specific cipher transformation is not supported in the current JDK.
     * @since 1.6
     */
    public static boolean isKeySizeSupported(int keySize) throws BtException {
        if (keySize <= 0) {
            throw new IllegalArgumentException("Negative key size: " + keySize);
        }

        int maxAllowedKeySizeBits;
        try {
            maxAllowedKeySizeBits = Cipher.getMaxAllowedKeyLength(transformation);
        } catch (NoSuchAlgorithmException e) {
            throw new BtException("Transformation is not supported: " + transformation);
        }

        return (keySize * 8) <= maxAllowedKeySizeBits;
    }

    /**
     * Create MSE cipher for connection initiator
     *
     * @param S Shared secret
     * @param torrentId Torrent id
     * @return MSE cipher configured for use by connection initiator
     * @since 1.2
     */
    public static MSECipher forInitiator(byte[] S, TorrentId torrentId) {
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
        digest.update(s.getBytes(StandardCharsets.US_ASCII));
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
