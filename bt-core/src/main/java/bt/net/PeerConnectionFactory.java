package bt.net;

import bt.metainfo.TorrentId;
import bt.net.crypto.MSEHandshakeProcessor;
import bt.protocol.Message;
import bt.protocol.crypto.EncryptionPolicy;
import bt.protocol.handler.MessageHandler;
import bt.torrent.TorrentRegistry;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

class PeerConnectionFactory {

    private MessageHandler<Message> messageHandler;
    private SocketChannelFactory socketChannelFactory;
    private MSEHandshakeProcessor cryptoHandshakeProcessor;

    private int maxTransferBlockSize;

    public PeerConnectionFactory(MessageHandler<Message> messageHandler,
                                 SocketChannelFactory socketChannelFactory,
                                 int maxTransferBlockSize,
                                 TorrentRegistry torrentRegistry,
                                 EncryptionPolicy encryptionPolicy) {
        this.messageHandler = messageHandler;
        this.socketChannelFactory = socketChannelFactory;
        this.maxTransferBlockSize = maxTransferBlockSize;
        this.cryptoHandshakeProcessor = new MSEHandshakeProcessor(torrentRegistry, encryptionPolicy);
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
        channel.configureBlocking(false);
        ByteChannel negotiatedChannel = incoming ?
                cryptoHandshakeProcessor.negotiateIncoming(channel) : cryptoHandshakeProcessor.negotiateOutgoing(channel, torrentId);
        int bufferSize = getBufferSize(maxTransferBlockSize);
        // TODO:
        // 1. pre-fill buffer with data that has already been received during the MSE handshake (i.e. IA)
        // 2. for outgoing connections, check local config ("alwaysNegotiateEncryption") and peer options
        // 3. for incoming connections, detect by first bytes if peer uses plaintext or MSE
        Supplier<Message> reader = new DefaultMessageReader(peer, negotiatedChannel, messageHandler, bufferSize);
        Consumer<Message> writer = new DefaultMessageWriter(negotiatedChannel, messageHandler, bufferSize);
        return new DefaultPeerConnection(peer, negotiatedChannel, reader, writer);
    }

    private static int getBufferSize(long maxTransferBlockSize) {
        if (maxTransferBlockSize > ((Integer.MAX_VALUE - 13) / 2)) {
            throw new IllegalArgumentException("Transfer block size is too large: " + maxTransferBlockSize);
        }
        return (int) (maxTransferBlockSize) * 2;
    }
}
