package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Message;
import bt.protocol.Protocols;
import bt.protocol.crypto.EncryptionPolicy;
import bt.protocol.handler.MessageHandler;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.DHPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

class PeerConnectionFactory {

    private MessageHandler<Message> messageHandler;
    private SocketChannelFactory socketChannelFactory;

    private int maxTransferBlockSize;

    public PeerConnectionFactory(MessageHandler<Message> messageHandler,
                                 SocketChannelFactory socketChannelFactory,
                                 int maxTransferBlockSize) {
        this.messageHandler = messageHandler;
        this.socketChannelFactory = socketChannelFactory;
        this.maxTransferBlockSize = maxTransferBlockSize;
    }

    public DefaultPeerConnection createConnection(Peer peer) throws IOException {
        Objects.requireNonNull(peer);

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel;
        try {
            channel = socketChannelFactory.getChannel(inetAddress, port);
        } catch (IOException e) {
            throw new IOException("Failed to create peer connection (" + inetAddress + ":" + port + ")", e);
        }

        return createConnection(peer, channel);
    }

    public DefaultPeerConnection createConnection(Peer peer, SocketChannel channel) throws IOException {
        return new DefaultPeerConnection(messageHandler, peer, channel, maxTransferBlockSize);
    }

    private BigInteger Y = BigInteger.valueOf(1); // private key: random 128/160 bit integer (configurable length/provider)
    // 768-bit prime
    private BigInteger P = new BigInteger("0xFFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563", 16);
    private BigInteger G = BigInteger.valueOf(2);

    private ByteChannel encryptedChannel(SocketChannel channel, TorrentId torrentId, EncryptionPolicy encryptionPolicy) throws IOException {
        /**
         * Blocking steps:
         *
         * 1. A->B: Diffie Hellman Ya, PadA
         * 2. B->A: Diffie Hellman Yb, PadB
         * 3. A->B: HASH('req1', S), HASH('req2', SKEY) xor HASH('req3', S), ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)), ENCRYPT(IA)
         * 4. B->A: ENCRYPT(VC, crypto_select, len(padD), padD), ENCRYPT2(Payload Stream)
         * 5. A->B: ENCRYPT2(Payload Stream)
         */

        ByteBuffer buf = ByteBuffer.allocateDirect(128 * 1024);

        // 1. send our public key
        Key localPublicKey = getDiffieHellmanKey(Y, P, G); // our 768-bit public key
        buf.put(localPublicKey.getEncoded());
        buf.put(getPadding(512));

        // write
        buf.flip();
        channel.write(buf);
        buf.clear();

        // 2. receive peer's public key
        Duration timeout = Duration.ofSeconds(30);
        int min = 96, limit = 608;
        read(channel, buf, timeout, min, limit);
        byte[] bytes = new byte[min];
        buf.flip();
        buf.get(bytes);
        buf.clear();

        // calculate shared secret S
        BigInteger peerPublicKey = new BigInteger(bytes);
        BigInteger S = peerPublicKey.xor(Y).mod(P);
        byte[] VC = getVerificationConstant();

        MessageDigest digest = getDigest("SHA-1");
        // 3. A->B:
        // - HASH('req1', S)
        digest.update("req1".getBytes("ASCII"));
        digest.update(S.toByteArray());
        buf.put(digest.digest());
        // - HASH('req2', SKEY) xor HASH('req3', S)
        digest.update("req2".getBytes("ASCII"));
        digest.update(torrentId.getBytes());
        byte[] b1 = digest.digest();
        digest.update("req3".getBytes("ASCII"));
        digest.update(S.toByteArray());
        byte[] b2 = digest.digest();
        buf.put(xor(b1, b2));
        // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
        Key initiatorKey = getInitiatorEncryptionKey(S.toByteArray(), torrentId.getBytes());
        OutputStream out = getEncryptedOutputStream(channel, initiatorKey);
        out.write(VC);
        out.write(getCryptoProvideBitfield(encryptionPolicy));
        byte[] padding = getZeroPadding(512);
        out.write(Protocols.getShortBytes(padding.length));
        out.write(padding);
        // - ENCRYPT(IA)
        // do not write IA (initial payload data) for now, wait for encryption negotiation
        out.write(0); // IA length = 0

        // write
        buf.flip();
        channel.write(buf);
        buf.clear();

        // 4. B->A:
        // - ENCRYPT(VC, crypto_select, len(padD), padD)
        // - ENCRYPT2(Payload Stream)
        Key receiverKey = getReceiverEncryptionKey(S.toByteArray(), torrentId.getBytes());
        InputStream in = getDecryptedInputStream(channel, receiverKey);
        read(in, buf, timeout, 14, 14 + 512);
        int received = buf.position();
        buf.flip();
        byte[] theirVC = new byte[8];
        buf.get(theirVC);
        if (!Arrays.equals(VC, theirVC)) {
            throw new IllegalStateException("Invalid VC: "+ Arrays.toString(theirVC));
        }
        byte[] crypto_select = new byte[4];
        buf.get(crypto_select);
        EncryptionPolicy negotiatedEncryptionPolicy = selectPolicy(crypto_select, encryptionPolicy)
                .orElseThrow(() -> new IllegalStateException("Failed to negotiate the encryption policy"));
        int theirPadding = Protocols.readShort(buf);
        buf.limit(received);
        buf.position(14 + theirPadding);

        switch (negotiatedEncryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                return buf.hasRemaining()? buildChannel(channel, buf) : channel;
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return buf.hasRemaining()? buildChannel(channel, in, out, buf) : buildChannel(channel, in, out);
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + negotiatedEncryptionPolicy.name());
            }
        }
    }

    private Key getDiffieHellmanKey(BigInteger Y, BigInteger P, BigInteger G) {
        try {
            KeyFactory factory = KeyFactory.getInstance("DiffieHellman");
            return factory.generatePublic(new DHPublicKeySpec(Y, P, G));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getPadding(int length) {
        // todo: use this constructor everywhere in the project
        Random r = new Random();
        byte[] padding = new byte[r.nextInt(length)];
        for (int i = 0; i < padding.length; i++) {
            padding[i] = (byte) r.nextInt(256);
        }
        return padding;
    }

    private byte[] getZeroPadding(int length) {
        // todo: use this constructor everywhere in the project
        Random r = new Random();
        return new byte[r.nextInt(length)];
    }

    private void read(SocketChannel channel, ByteBuffer buf, Duration timeout, int min, int limit) throws IOException {
        boolean blocking = channel.isBlocking();
        channel.configureBlocking(false);
        try {
            tryRead(channel, buf, timeout, min, limit);
        } finally {
            channel.configureBlocking(blocking);
        }
    }

    private void read(InputStream in, ByteBuffer buf, Duration timeout, int min, int limit) throws IOException {
        tryRead(Channels.newChannel(in), buf, timeout, min, limit);
    }

    private void tryRead(ReadableByteChannel channel, ByteBuffer buf, Duration timeout, int min, int limit) throws IOException {
        long t1 = System.currentTimeMillis();
        int read_total = 0;
        int times_nothing_received = 0;
        do {
            int read = channel.read(buf);
            if (read == 0) {
                times_nothing_received++;
            } else {
                read_total += read;
            }
            if (read_total > limit) {
                throw new IllegalStateException("More than " + limit + " bytes received: " + read_total);
            } else if (read_total >= min && times_nothing_received >= 3) {
                // hasn't received anything for 3 times in a row; assuming all data has arrived
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for data", e);
            }
        } while (System.currentTimeMillis() - t1 <= timeout.toMillis());

        if (read_total < min) {
            throw new IllegalStateException("Less than " + min + " bytes received: " + read_total);
        }
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

    private OutputStream getEncryptedOutputStream(WritableByteChannel channel, Key key) {
        String transformation = "ARCFOUR";
        Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, transformation, key);
        return new CipherOutputStream(Channels.newOutputStream(channel), cipher);
    }

    private InputStream getDecryptedInputStream(ReadableByteChannel channel, Key key) {
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

    private byte[] getVerificationConstant() {
        return new byte[8];
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

    private byte[] getCryptoSelectBitfield(EncryptionPolicy encryptionPolicy) {
        byte[] crypto_select = new byte[4];
        switch (encryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                crypto_select[3] = 1; // only 0x01
                break;
            }

            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                crypto_select[3] = 2; // only 0x02
                break;
            }
            default: {
                throw new IllegalStateException("Can't select crypto options, unknown encryption policy: " + encryptionPolicy.name());
            }
        }
        return crypto_select;
    }

    private Optional<EncryptionPolicy> selectPolicy(byte[] crypto_provide, EncryptionPolicy localEncryptionPolicy) {
        boolean plaintextProvided = (crypto_provide[3] & 0x01) == 0x01;
        boolean encryptionProvided = (crypto_provide[3] & 0x02) == 0x02;
        if (!plaintextProvided && !encryptionProvided) {
            return Optional.empty();
        }
        switch (localEncryptionPolicy) {
            case REQUIRE_PLAINTEXT: {
                return plaintextProvided? Optional.of(EncryptionPolicy.REQUIRE_PLAINTEXT) : Optional.empty();
            }
            case PREFER_PLAINTEXT: {
                return plaintextProvided? Optional.of(EncryptionPolicy.REQUIRE_PLAINTEXT)
                        : Optional.of(EncryptionPolicy.REQUIRE_ENCRYPTED);
            }
            case PREFER_ENCRYPTED: {
                return encryptionProvided? Optional.of(EncryptionPolicy.REQUIRE_ENCRYPTED)
                        : Optional.of(EncryptionPolicy.REQUIRE_PLAINTEXT);
            }
            case REQUIRE_ENCRYPTED: {
                return encryptionProvided? Optional.of(EncryptionPolicy.REQUIRE_ENCRYPTED) : Optional.empty();
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + localEncryptionPolicy.name());
            }
        }
    }

    private ByteChannel buildChannel(ByteChannel origin, InputStream in, OutputStream out, ByteBuffer remaining) {
        return buildChannel(buildChannel(origin, remaining), in, out);
    }

    private ByteChannel buildChannel(ByteChannel origin, ByteBuffer received) {
        return new ByteChannel() {
            @Override
            public int read(ByteBuffer dst) throws IOException {
                int remaining = received.remaining();
                if (remaining == 0) {
                    return origin.read(dst);
                } else if (remaining <= dst.remaining()) {
                    dst.put(received);
                    return remaining;
                } else {
                    return 0;
                }
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                return origin.write(src);
            }

            @Override
            public boolean isOpen() {
                return origin.isOpen();
            }

            @Override
            public void close() throws IOException {
                origin.close();
            }
        };
    }

    private ByteChannel buildChannel(Channel origin, InputStream in, OutputStream out) {
        return new ByteChannel() {
            private Queue<byte[]> leftovers = new PriorityQueue<>();

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int written = 0;

                byte[] buf;
                while ((buf = leftovers.peek()) != null) {
                    if (buf.length <= dst.remaining()) {
                        dst.put(buf);
                        leftovers.remove();
                        written += buf.length;
                    } else {
                        return written;
                    }
                }

                buf = new byte[8192];
                int read = in.read(buf);

                boolean success;
                if (read <= dst.remaining()) {
                    dst.put(buf, 0, read);
                    written += read;
                    success = true;
                } else {
                    leftovers.add(Arrays.copyOfRange(buf, 0, read));
                    success = false;
                }

                return success ? written : 0;
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                byte[] buf = new byte[src.remaining()];
                src.get(buf);
                out.write(buf);
                return buf.length;
            }

            @Override
            public boolean isOpen() {
                return origin.isOpen();
            }

            @Override
            public void close() throws IOException {
                // not closing cipher streams
                try {
                    origin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
