package bt.net.crypto;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.protocol.Protocols;
import bt.protocol.crypto.EncryptionPolicy;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public class MSEHandshakeProcessor {

    private static final Duration receiveTimeout = Duration.ofSeconds(30);
    private static final int paddingMaxLength = 512;
    private static final byte[] VC = new byte[8];

    private MSEKeyPairGenerator keyGenerator;
    private TorrentRegistry torrentRegistry;
    private EncryptionPolicy localEncryptionPolicy;

    public MSEHandshakeProcessor(TorrentRegistry torrentRegistry,
                                 EncryptionPolicy localEncryptionPolicy) {
        this.keyGenerator = new MSEKeyPairGenerator();
        this.torrentRegistry = torrentRegistry;
        this.localEncryptionPolicy = localEncryptionPolicy;
    }

    public ByteChannel negotiateOutgoing(ByteChannel channel, TorrentId torrentId) throws IOException {
        /**
         * Blocking steps:
         *
         * 1. A->B: Diffie Hellman Ya, PadA
         * 2. B->A: Diffie Hellman Yb, PadB
         * 3. A->B:
         *  - HASH('req1', S),
         *  - HASH('req2', SKEY) xor HASH('req3', S),
         *  - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)),
         *  - ENCRYPT(IA)
         * 4. B->A:
         * - ENCRYPT(VC, crypto_select, len(padD), padD),
         * - ENCRYPT2(Payload Stream)
         * 5. A->B: ENCRYPT2(Payload Stream)
         */

        ByteBuffer in = ByteBuffer.allocateDirect(128 * 1024);
        ByteBuffer out = ByteBuffer.allocateDirect(128 * 1024);
        DataReader reader = new DataReader(channel, receiveTimeout);

        // 1. A->B: Diffie Hellman Ya, PadA
        // send our public key
        KeyPair keys = keyGenerator.generateKeyPair();
        out.put(keys.getPublic().getEncoded());
        out.put(getPadding(paddingMaxLength));
        out.flip();
        channel.write(out);
        out.clear();

        // 2. B->A: Diffie Hellman Yb, PadB
        // receive peer's public key
        reader.read(in, keyGenerator.getKeySize(), keyGenerator.getKeySize() + paddingMaxLength);
        in.flip();
        BigInteger peerPublicKey = BigIntegers.fromBytes(in, keyGenerator.getKeySize());
        in.clear(); // ignore padding

        // calculate shared secret S
        BigInteger S = keyGenerator.calculateSharedSecret(peerPublicKey, keys.getPrivate());

        // 3. A->B:
        MessageDigest digest = getDigest("SHA-1");
        // - HASH('req1', S)
        digest.update("req1".getBytes("ASCII"));
        digest.update(BigIntegers.toByteArray(S));
        out.put(digest.digest());
        // - HASH('req2', SKEY) xor HASH('req3', S)
        digest.update("req2".getBytes("ASCII"));
        digest.update(torrentId.getBytes());
        byte[] b1 = digest.digest();
        digest.update("req3".getBytes("ASCII"));
        digest.update(BigIntegers.toByteArray(S));
        byte[] b2 = digest.digest();
        out.put(xor(b1, b2));
        // write
        out.flip();
        channel.write(out);
        out.clear();

        MSECipher cipher = MSECipher.forInitiator(S, torrentId);
        ByteChannel encryptedChannel = new EncryptedChannel(channel, cipher);
        // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
        out.put(VC);
        out.put(getCryptoProvideBitfield(localEncryptionPolicy));
        byte[] padding = getZeroPadding(512);
        out.put(Protocols.getShortBytes(padding.length));
        out.put(padding);
        // - ENCRYPT(IA)
        // do not write IA (initial payload data) for now, wait for encryption negotiation
        out.put((byte) 0); // IA length = 0
        out.put((byte) 0);
        out.flip();
        encryptedChannel.write(out);
        out.clear();

        // 4. B->A:
        new DataReader(encryptedChannel, receiveTimeout).read(in, 14, 14 + 512);
        // - ENCRYPT(VC, crypto_select, len(padD), padD)
        in.flip();

        byte[] theirVC = new byte[8];
        in.get(theirVC);
        if (!Arrays.equals(VC, theirVC)) {
            throw new IllegalStateException("Invalid verification constant: " + Arrays.toString(theirVC));
        }
        byte[] crypto_select = new byte[4];
        in.get(crypto_select);
        EncryptionPolicy negotiatedEncryptionPolicy = selectPolicy(crypto_select, localEncryptionPolicy);

        int theirPadding = in.getShort();
        // assume that all data has been received, so the whole padding block is present
        for (int i = 0; i < theirPadding; i++) {
            in.get();
        }

        // - ENCRYPT2(Payload Stream)
        switch (negotiatedEncryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                return channel;
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return encryptedChannel;
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + negotiatedEncryptionPolicy.name());
            }
        }
    }

    public ByteChannel negotiateIncoming(ByteChannel channel) throws IOException {
        /**
         * Blocking steps:
         *
         * 1. A->B: Diffie Hellman Ya, PadA
         * 2. B->A: Diffie Hellman Yb, PadB
         * 3. A->B:
         *  - HASH('req1', S),
         *  - HASH('req2', SKEY) xor HASH('req3', S),
         *  - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)),
         *  - ENCRYPT(IA)
         * 4. B->A:
         *  - ENCRYPT(VC, crypto_select, len(padD), padD),
         *  - ENCRYPT2(Payload Stream)
         * 5. A->B: ENCRYPT2(Payload Stream)
         */

        ByteBuffer in = ByteBuffer.allocateDirect(128 * 1024);
        ByteBuffer out = ByteBuffer.allocateDirect(128 * 1024);
        DataReader reader = new DataReader(channel, receiveTimeout);

        // 1. A->B: Diffie Hellman Ya, PadA
        // receive initiator's public key
        reader.read(in, keyGenerator.getKeySize(), keyGenerator.getKeySize() + paddingMaxLength);
        in.flip();
        BigInteger peerPublicKey = BigIntegers.fromBytes(in, keyGenerator.getKeySize());
        in.clear(); // ignore padding

        // 2. B->A: Diffie Hellman Yb, PadB
        // send our public key
        KeyPair keys = keyGenerator.generateKeyPair();
        out.put(keys.getPublic().getEncoded());
        out.put(getPadding(paddingMaxLength));
        out.flip();
        channel.write(out);
        out.clear();

        // calculate shared secret S
        BigInteger S = keyGenerator.calculateSharedSecret(peerPublicKey, keys.getPrivate());

        // 3. A->B:
        MessageDigest digest = getDigest("SHA-1");
        // receive all data
        reader.read(in, 20 + 20 + 8 + 4 + 2 + 0 + 2, 20 + 20 + 8 + 4 + 2 + 512 + 2);
        in.flip();

        byte[] bytes = new byte[20];
        // - HASH('req1', S)
        in.get(bytes); // read S hash
        digest.update("req1".getBytes("ASCII"));
        digest.update(BigIntegers.toByteArray(S));
        byte[] req1hash = digest.digest();
        if (!Arrays.equals(req1hash, bytes)) {
            throw new IllegalStateException("Shared secret does not match");
        }
        // - HASH('req2', SKEY) xor HASH('req3', S)
        in.get(bytes); // read SKEY/S hash
        Torrent requestedTorrent = null;
        digest.update("req3".getBytes("ASCII"));
        digest.update(BigIntegers.toByteArray(S));
        byte[] b2 = digest.digest();
        for (Torrent torrent : torrentRegistry.getTorrents()) {
            digest.update("req2".getBytes("ASCII"));
            digest.update(torrent.getTorrentId().getBytes());
            byte[] b1 = digest.digest();
            if (Arrays.equals(xor(b1, b2), bytes)) {
                requestedTorrent = torrent;
                break;
            }
        }
        // check that torrent is supported and active
        boolean err = false;
        if (requestedTorrent == null) {
            err = true;
        } else {
            Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(requestedTorrent);
            if (!descriptor.isPresent() || !descriptor.get().isActive()) {
                err = true;
            }
        }
        if (err) {
            throw new IllegalStateException("Unsupported/inactive torrent requested");
        }

        MSECipher cipher = MSECipher.forReceiver(S, requestedTorrent.getTorrentId());
        ByteChannel encryptedChannel = new EncryptedChannel(channel, cipher);

        // derypt encrypted leftovers from step #3
        int pos = in.position();
        byte[] leftovers = new byte[in.remaining()];
        in.get(leftovers);
        in.position(pos);
        try {
            in.put(cipher.getDecryptionCipher().update(leftovers));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt leftover bytes: " + leftovers.length);
        }
        in.position(pos);

        if (in.remaining() > 16 + paddingMaxLength) {
            throw new IllegalArgumentException("Too much initial data");
        }

        int min = Math.max(16 - in.remaining(), 0);
        int limit = 16 + paddingMaxLength - in.remaining();

        // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
        if (limit > 0) { // if limit is 0 then all possible data has already been received
            int offset = in.position();
            in.position(in.limit());
            in.limit(in.capacity());
            new DataReader(encryptedChannel, receiveTimeout).read(in, min, limit);
            in.flip();
            in.position(offset);
        }

        byte[] theirVC = new byte[8];
        in.get(theirVC);
        if (!Arrays.equals(VC, theirVC)) {
            throw new IllegalStateException("Invalid VC: "+ Arrays.toString(theirVC));
        }

        byte[] crypto_provide = new byte[4];
        in.get(crypto_provide);
        EncryptionPolicy negotiatedEncryptionPolicy = selectPolicy(crypto_provide, localEncryptionPolicy);

        int theirPadding = in.getShort();
        // assume that all data has been received, so the whole padding block is present
        for (int i = 0; i < theirPadding; i++) {
            in.get();
        }

        // skip Initial Payload length
        in.get();
        in.get();

        // 4. B->A:
        // - ENCRYPT(VC, crypto_select, len(padD), padD)
        // - ENCRYPT2(Payload Stream)
        out.put(VC);
        out.put(getCryptoProvideBitfield(negotiatedEncryptionPolicy));
        byte[] padding = getZeroPadding(512);
        out.put(Protocols.getShortBytes(padding.length));
        out.put(padding);
        out.flip();
        encryptedChannel.write(out);
        out.clear();

        switch (negotiatedEncryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                return channel;
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return encryptedChannel;
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + negotiatedEncryptionPolicy.name());
            }
        }
    }

    private byte[] getPadding(int length) {
        // todo: use this constructor everywhere in the project
        Random r = new Random();
        byte[] padding = new byte[r.nextInt(length + 1)];
        for (int i = 0; i < padding.length; i++) {
            padding[i] = (byte) r.nextInt(256);
        }
        return padding;
    }

    private byte[] getZeroPadding(int length) {
        // todo: use this constructor everywhere in the project
        Random r = new Random();
        return new byte[r.nextInt(length + 1)];
    }

    private MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] xor(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new IllegalStateException("Lengths do not match: " + b1.length + ", " + b2.length);
        }
        byte[] result = new byte[b1.length];
        for (int i = 0; i < b1.length; i++) {
            result[i] = (byte) (b1[i] ^ b2[i]);
        }
        return result;
    }

    private byte[] getCryptoProvideBitfield(EncryptionPolicy encryptionPolicy) {
        byte[] crypto_provide = new byte[4];
        switch (encryptionPolicy) {
            case REQUIRE_PLAINTEXT: {
                crypto_provide[3] = 1; // only 0x01
                break;
            }
            case PREFER_PLAINTEXT:
            case PREFER_ENCRYPTED: {
                crypto_provide[3] = 3; // both 0x01 and 0x02
                break;
            }
            case REQUIRE_ENCRYPTED: {
                crypto_provide[3] = 2; // only 0x02
                break;
            }
            default: {
                // do nothing, bitfield is all zeros
            }
        }
        return crypto_provide;
    }

    private EncryptionPolicy selectPolicy(byte[] crypto_provide, EncryptionPolicy localEncryptionPolicy) {
        boolean plaintextProvided = (crypto_provide[3] & 0x01) == 0x01;
        boolean encryptionProvided = (crypto_provide[3] & 0x02) == 0x02;

        EncryptionPolicy selected = null;
        if (plaintextProvided || encryptionProvided) {
            switch (localEncryptionPolicy) {
                case REQUIRE_PLAINTEXT: {
                    if (plaintextProvided) {
                        selected = EncryptionPolicy.REQUIRE_PLAINTEXT;
                    }
                    break;
                }
                case PREFER_PLAINTEXT: {
                    selected = plaintextProvided ? EncryptionPolicy.REQUIRE_PLAINTEXT : EncryptionPolicy.REQUIRE_ENCRYPTED;
                    break;
                }
                case PREFER_ENCRYPTED: {
                    selected = encryptionProvided ? EncryptionPolicy.REQUIRE_ENCRYPTED : EncryptionPolicy.REQUIRE_PLAINTEXT;
                    break;
                }
                case REQUIRE_ENCRYPTED: {
                    if (encryptionProvided) {
                        selected = EncryptionPolicy.REQUIRE_ENCRYPTED;
                    }
                    break;
                }
                default: {
                    throw new IllegalStateException("Unknown encryption policy: " + localEncryptionPolicy.name());
                }
            }
        }

        if (selected == null) {
            throw new IllegalStateException("Failed to negotiate the encryption policy: local policy (" +
                    localEncryptionPolicy.name() + "), peer's policy (" + Arrays.toString(crypto_provide) + ")");
        }
        return selected;
    }
}
