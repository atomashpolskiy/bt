package bt.net;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.crypto.DiffieHellman;
import bt.net.crypto.NegotiateEncryptionPolicy;
import bt.net.crypto.ReceiveData;
import bt.net.crypto.SendData;
import bt.net.crypto.StreamCipher;
import bt.protocol.Message;
import bt.protocol.crypto.EncryptionPolicy;
import bt.protocol.handler.MessageHandler;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

class PeerConnectionFactory {

    private MessageHandler<Message> messageHandler;
    private SocketChannelFactory socketChannelFactory;
    private TorrentRegistry torrentRegistry;
    private EncryptionPolicy encryptionPolicy;

    private int maxTransferBlockSize;

    public PeerConnectionFactory(MessageHandler<Message> messageHandler,
                                 SocketChannelFactory socketChannelFactory,
                                 int maxTransferBlockSize,
                                 TorrentRegistry torrentRegistry,
                                 EncryptionPolicy encryptionPolicy) {
        this.messageHandler = messageHandler;
        this.socketChannelFactory = socketChannelFactory;
        this.maxTransferBlockSize = maxTransferBlockSize;
        this.torrentRegistry = torrentRegistry;
        this.encryptionPolicy = encryptionPolicy;
    }

    public DefaultPeerConnection createOutgoingConnection(Peer peer, TorrentId torrentId) throws IOException {
        Objects.requireNonNull(peer);

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel;
        try {
            channel = socketChannelFactory.getChannel(inetAddress, port);
        } catch (IOException e) {
            throw new IOException("Failed to create peer connection (" + inetAddress + ":" + port + ")", e);
        }

        return createConnection(peer, torrentId, channel, false);
    }

    public DefaultPeerConnection createIncomingConnection(Peer peer, SocketChannel channel) throws IOException {
        return createConnection(peer, null, channel, true);
    }

    private DefaultPeerConnection createConnection(Peer peer, TorrentId torrentId, SocketChannel channel, boolean incoming) throws IOException {
        // some magic going on here:
        // we need to trick encrypted streams into thinking that the channel is blocking,
        // while using a non-blocking socket channel
        channel.configureBlocking(false);
        DelegatingByteChannel proxyChannel = new DelegatingByteChannel(channel);
        proxyChannel.configureBlocking(true);

        ByteChannel negotiatedChannel = incoming ?
                encryptIncomingChannel(proxyChannel) : encryptOutgoingChannel(proxyChannel, torrentId);
        return new DefaultPeerConnection(peer, negotiatedChannel, messageHandler, getBufferSize(maxTransferBlockSize));
    }

    private static int getBufferSize(long maxTransferBlockSize) {
        if (maxTransferBlockSize > ((Integer.MAX_VALUE - 13) / 2)) {
            throw new IllegalArgumentException("Transfer block size is too large: " + maxTransferBlockSize);
        }
        return (int) (maxTransferBlockSize) * 2;
    }

    // private key: random 128/160 bit integer (configurable length/provider)
    private BigInteger Y = new DiffieHellman().generatePrivateKey();

    private Duration timeout = Duration.ofSeconds(5);

    private ByteChannel encryptIncomingChannel(ByteChannel channel) throws IOException {
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

        // 1. A->B: Diffie Hellman Ya, PadA
        // read initiator's public key
        new ReceiveData(channel, timeout).execute(buf, 96, 608);

        buf.flip();
        BigInteger peerPublicKey = new DiffieHellman().parseKey(buf);
        buf.clear();

        // 2. B->A: Diffie Hellman Yb, PadB
        // write our key
        BigInteger localPublicKey = new DiffieHellman().createPublicKey(Y); // our 768-bit public key
        buf.put(new DiffieHellman().toByteArray(localPublicKey, 96));
        buf.put(getPadding(512));
        buf.flip();
        new SendData(channel).execute(buf);
        buf.clear();

        // calculate shared secret S
        BigInteger S = new DiffieHellman().calculateSharedSecret(peerPublicKey, Y);

        MessageDigest digest = getDigest("SHA-1");
        // 3. A->B:
        // receive all data
        new ReceiveData(channel, timeout).execute(buf, 20 + 20 + 8 + 4 + 2 + 0 + 2, 20 + 20 + 8 + 4 + 2 + 512 + 2);
        buf.flip();

        byte[] bytes = new byte[20];
        // - HASH('req1', S)
        buf.get(bytes); // read S hash
        digest.update("req1".getBytes("ASCII"));
        digest.update(new DiffieHellman().toByteArray(S));
        byte[] req1hash = digest.digest();
        if (!Arrays.equals(req1hash, bytes)) {
            throw new IllegalStateException("Invalid shared secret hash");
        }
        // - HASH('req2', SKEY) xor HASH('req3', S)
        buf.get(bytes); // read SKEY/S hash
        Torrent requestedTorrent = null;
        digest.update("req3".getBytes("ASCII"));
        digest.update(new DiffieHellman().toByteArray(S));
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
            throw new IllegalStateException("Unsupported/inactive torrent");
        }

        // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
        StreamCipher cipher = StreamCipher.forReceiver(S, requestedTorrent.getTorrentId());
        InputStream in = cipher.encryptIncomingChannel(channel);

        // 4. B->A:
        // - ENCRYPT(VC, crypto_select, len(padD), padD)
        // - ENCRYPT2(Payload Stream)
        OutputStream out = cipher.encryptOugoingChannel(channel);


        EncryptionPolicy negotiatedEncryptionPolicy = new NegotiateEncryptionPolicy(encryptionPolicy).negotiateIncoming(in, out, buf);
        if (buf.hasRemaining()) {
            throw new IllegalStateException("Should not happen"); // remove this after verifying the code
        }

        switch (negotiatedEncryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                return channel;
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return buildChannel(in, out);
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + negotiatedEncryptionPolicy.name());
            }
        }
    }

    private ByteChannel encryptOutgoingChannel(ByteChannel channel, TorrentId torrentId) throws IOException {
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
        BigInteger localPublicKey = new DiffieHellman().createPublicKey(Y); // our 768-bit public key
        buf.put(new DiffieHellman().toByteArray(localPublicKey, 96));
        buf.put(getPadding(512));

        // write
        buf.flip();
        new SendData(channel).execute(buf);
        buf.clear();

        // 2. receive peer's public key
        new ReceiveData(channel, timeout).execute(buf, 96, 608);
        buf.flip();
        BigInteger peerPublicKey = new DiffieHellman().parseKey(buf);
        buf.clear();

        // calculate shared secret S
        BigInteger S = new DiffieHellman().calculateSharedSecret(peerPublicKey, Y);


        MessageDigest digest = getDigest("SHA-1");
        // 3. A->B:
        // - HASH('req1', S)
        digest.update("req1".getBytes("ASCII"));
        digest.update(new DiffieHellman().toByteArray(S));
        buf.put(digest.digest());
        // - HASH('req2', SKEY) xor HASH('req3', S)
        digest.update("req2".getBytes("ASCII"));
        digest.update(torrentId.getBytes());
        byte[] b1 = digest.digest();
        digest.update("req3".getBytes("ASCII"));
        digest.update(new DiffieHellman().toByteArray(S));
        byte[] b2 = digest.digest();
        buf.put(xor(b1, b2));
        // write
        buf.flip();
        new SendData(channel).execute(buf);
        buf.clear();

        StreamCipher cipher = StreamCipher.forInitiator(S, torrentId);
        InputStream in = cipher.encryptIncomingChannel(channel);
        OutputStream out = cipher.encryptOugoingChannel(channel);
        EncryptionPolicy negotiatedEncryptionPolicy = new NegotiateEncryptionPolicy(encryptionPolicy).negotiateOutgoing(in, out);

        switch (negotiatedEncryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                return channel;
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                return buildChannel(in, out);
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
//        return padding;
        return new byte[0];
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

    private ByteChannel buildChannel(InputStream in, OutputStream out) {
        ReadableByteChannel cin = Channels.newChannel(in);
        WritableByteChannel cout = Channels.newChannel(out);
        return new ByteChannel() {
            @Override
            public int read(ByteBuffer dst) throws IOException {
                return cin.read(dst);
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                return cout.write(src);
            }

            @Override
            public boolean isOpen() {
                return cin.isOpen() || cout.isOpen();
            }

            @Override
            public void close() throws IOException {
                try {
                    cin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    cout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
