package bt.net.crypto;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.DefaultMessageReader;
import bt.net.DefaultMessageWriter;
import bt.net.DelegatingMessageReaderWriter;
import bt.net.MessageReaderWriter;
import bt.net.Peer;
import bt.protocol.DecodingContext;
import bt.protocol.Handshake;
import bt.protocol.Message;
import bt.protocol.Protocols;
import bt.protocol.crypto.EncryptionPolicy;
import bt.protocol.handler.MessageHandler;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MSEHandshakeProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MSEHandshakeProcessor.class);

    private static final Duration receiveTimeout = Duration.ofSeconds(30);
    private static final int paddingMaxLength = 512;
    private static final byte[] VC = new byte[8];

    private final MSEKeyPairGenerator keyGenerator;
    private final TorrentRegistry torrentRegistry;
    private final MessageHandler<Message> messageHandler;
    private final EncryptionPolicy localEncryptionPolicy;
    private final int bufferSize;

    public MSEHandshakeProcessor(TorrentRegistry torrentRegistry,
                                 MessageHandler<Message> messageHandler,
                                 EncryptionPolicy localEncryptionPolicy,
                                 int bufferSize) {
        this.keyGenerator = new MSEKeyPairGenerator();
        this.torrentRegistry = torrentRegistry;
        this.messageHandler = messageHandler;
        this.localEncryptionPolicy = localEncryptionPolicy;
        this.bufferSize = bufferSize;
    }

    public MessageReaderWriter negotiateOutgoing(Peer peer, ByteChannel channel, TorrentId torrentId) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Negotiating encryption for outgoing connection: {}", peer);
        }
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

        // check if the encryption negotiation can be skipped or preemptively aborted
        EncryptionPolicy peerEncryptionPolicy = peer.getOptions().getEncryptionPolicy();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Peer {} has encryption policy: {}", peer, peerEncryptionPolicy);
        }
        assertPolicyIsCompatible(peerEncryptionPolicy);

        ByteBuffer in = ByteBuffer.allocateDirect(bufferSize);
        ByteBuffer out = ByteBuffer.allocateDirect(bufferSize);

        if (peerEncryptionPolicy == EncryptionPolicy.REQUIRE_PLAINTEXT) {
            // if peer requires plaintext and we support it, then do not negotiate encryption and use plaintext right away
            return createReaderWriter(peer, channel, in, out);
        }

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
        digest.update(BigIntegers.toByteArray(S, keyGenerator.getKeySizeBits()));
        out.put(digest.digest());
        // - HASH('req2', SKEY) xor HASH('req3', S)
        digest.update("req2".getBytes("ASCII"));
        digest.update(torrentId.getBytes());
        byte[] b1 = digest.digest();
        digest.update("req3".getBytes("ASCII"));
        digest.update(BigIntegers.toByteArray(S, keyGenerator.getKeySizeBits()));
        byte[] b2 = digest.digest();
        out.put(xor(b1, b2));
        // write
        out.flip();
        channel.write(out);
        out.clear();

        MSECipher cipher = MSECipher.forInitiator(BigIntegers.toByteArray(S, keyGenerator.getKeySizeBits()), torrentId);
        ByteChannel encryptedChannel = new EncryptedChannel(channel, cipher.getDecryptionCipher(), cipher.getEncryptionCipher());
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
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Negotiated encryption policy: {}, peer: {}", negotiatedEncryptionPolicy, peer);
        }

        int theirPadding = in.getShort() & 0xFFFF;
        // assume that all data has been received, so the whole padding block is present
        for (int i = 0; i < theirPadding; i++) {
            in.get();
        }

        in.clear();
        out.clear();

        // - ENCRYPT2(Payload Stream)
        switch (negotiatedEncryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                return createReaderWriter(peer, channel, in, out);
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return createReaderWriter(peer, encryptedChannel, in, out);
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + negotiatedEncryptionPolicy.name());
            }
        }
    }

    public MessageReaderWriter negotiateIncoming(Peer peer, ByteChannel channel) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Negotiating encryption for incoming connection: {}", peer);
        }
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

        ByteBuffer in = ByteBuffer.allocateDirect(bufferSize);
        ByteBuffer out = ByteBuffer.allocateDirect(bufferSize);
        DataReader reader = new DataReader(channel, receiveTimeout);

        // 1. A->B: Diffie Hellman Ya, PadA
        // receive initiator's public key
        // do not specify lower threshold on the amount of bytes to receive,
        // as we will try to decode plaintext message of an unknown length first
        reader.read(in, 1, keyGenerator.getKeySize() + paddingMaxLength);
        in.flip();
        // try to determine the protocol from the first received bytes
        DecodingContext context = new DecodingContext(peer);
        int consumed = 0;
        try {
             consumed = messageHandler.decode(context, in);
        } catch (Exception e) {
            // ignore
        }
        in.position(0); // reset buffer (message will be decoded once again by the upper layer)
        // TODO: can this be done without knowing the protocol specifics? (KeepAlive can be especially misleading: 0x00 0x00 0x00 0x00)
        if (consumed > 0 && context.getMessage() instanceof Handshake) {
            // decoding was successful, can use plaintext (if supported)
            assertPolicyIsCompatible(EncryptionPolicy.REQUIRE_PLAINTEXT);
            return createReaderWriter(peer, channel, in, out);
        }

        // verify that there is a sufficient amount of bytes to decode peer's public key
        if (in.remaining() < keyGenerator.getKeySize()) {
            throw new IllegalStateException("Less than " + keyGenerator.getKeySize() + " bytes received");
        }

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
        digest.update(BigIntegers.toByteArray(S, keyGenerator.getKeySizeBits()));
        byte[] req1hash = digest.digest();
        if (!Arrays.equals(req1hash, bytes)) {
            throw new IllegalStateException("Shared secret does not match");
        }
        // - HASH('req2', SKEY) xor HASH('req3', S)
        in.get(bytes); // read SKEY/S hash
        Torrent requestedTorrent = null;
        digest.update("req3".getBytes("ASCII"));
        digest.update(BigIntegers.toByteArray(S, keyGenerator.getKeySizeBits()));
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

        MSECipher cipher = MSECipher.forReceiver(BigIntegers.toByteArray(S, keyGenerator.getKeySizeBits()), requestedTorrent.getTorrentId());
        ByteChannel encryptedChannel = new EncryptedChannel(channel, cipher.getDecryptionCipher(), cipher.getEncryptionCipher());

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
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Negotiated encryption policy: {}, peer: {}", negotiatedEncryptionPolicy, peer);
        }

        int theirPadding = in.getShort() & 0xFFFF;
        // assume that all data has been received, so the whole padding block is present
        for (int i = 0; i < theirPadding; i++) {
            in.get();
        }

        // Initial Payload length (0..65535 bytes)
        int initialPayloadLength = in.getShort() & 0xFFFF;
        in.limit(in.position() + initialPayloadLength);
        in.compact();

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
                return createReaderWriter(peer, channel, in, out);
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return createReaderWriter(peer, encryptedChannel, in, out);
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + negotiatedEncryptionPolicy.name());
            }
        }
    }

    private void assertPolicyIsCompatible(EncryptionPolicy peerEncryptionPolicy) {
        if (!localEncryptionPolicy.isCompatible(peerEncryptionPolicy)) {
            throw new RuntimeException("Encryption policies are incompatible: peer's (" + peerEncryptionPolicy.name()
                    + "), local (" + localEncryptionPolicy.name() + ")");
        }
    }

    private MessageReaderWriter createReaderWriter(Peer peer, ByteChannel channel, ByteBuffer in, ByteBuffer out) {
        Supplier<Message> reader = new DefaultMessageReader(peer, channel, messageHandler, in);
        Consumer<Message> writer = new DefaultMessageWriter(channel, messageHandler, out);
        return new DelegatingMessageReaderWriter(reader, writer);
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
