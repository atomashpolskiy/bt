package bt.net;

import bt.metainfo.TorrentId;
import bt.net.crypto.MSEHandshakeProcessor;
import bt.protocol.Message;
import bt.protocol.crypto.EncryptionPolicy;
import bt.protocol.handler.MessageHandler;
import bt.torrent.TorrentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.Objects;

class PeerConnectionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionFactory.class);

    private SocketChannelFactory socketChannelFactory;
    private MSEHandshakeProcessor cryptoHandshakeProcessor;

    public PeerConnectionFactory(MessageHandler<Message> messageHandler,
                                 SocketChannelFactory socketChannelFactory,
                                 int maxTransferBlockSize,
                                 TorrentRegistry torrentRegistry,
                                 EncryptionPolicy encryptionPolicy) {
        this.socketChannelFactory = socketChannelFactory;
        this.cryptoHandshakeProcessor = new MSEHandshakeProcessor(
                torrentRegistry, messageHandler, encryptionPolicy, getBufferSize(maxTransferBlockSize));
    }

    private static int getBufferSize(long maxTransferBlockSize) {
        if (maxTransferBlockSize > ((Integer.MAX_VALUE - 13) / 2)) {
            throw new IllegalArgumentException("Transfer block size is too large: " + maxTransferBlockSize);
        }
        return (int) (maxTransferBlockSize) * 2;
    }

    public DefaultPeerConnection createOutgoingConnection(Peer peer, TorrentId torrentId) throws IOException {
        Objects.requireNonNull(peer);

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel = null;
        try {
            channel = socketChannelFactory.getChannel(inetAddress, port);
            return createConnection(peer, torrentId, channel, false);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new IOException("Failed to create peer connection (" + inetAddress + ":" + port + ")", e);
        }
    }

    public DefaultPeerConnection createIncomingConnection(Peer peer, SocketChannel channel) throws IOException {
        try {
            return createConnection(peer, null, channel, true);
        } catch (IOException e) {
            closeQuietly(channel);
            throw e;
        }
    }

    private DefaultPeerConnection createConnection(Peer peer, TorrentId torrentId, SocketChannel channel, boolean incoming) throws IOException {
        // sanity check
        if (!incoming && torrentId == null) {
            throw new IllegalStateException("Requested outgoing connection without torrent ID. Peer: " + peer);
        }

        channel.configureBlocking(false);

        MessageReaderWriter readerWriter = incoming ?
                cryptoHandshakeProcessor.negotiateIncoming(peer, channel)
                : cryptoHandshakeProcessor.negotiateOutgoing(peer, channel, torrentId);
        // TODO:
        // 1. pre-fill buffer with data that has already been received during the MSE handshake (i.e. IA)
        // 2. for outgoing connections, check local config ("alwaysNegotiateEncryption") and peer options
        // 3. for incoming connections, detect by first bytes if peer uses plaintext or MSE
        return new DefaultPeerConnection(peer, channel, readerWriter);
    }

    private void closeQuietly(SocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e1) {
                try {
                    LOGGER.warn("Failed to close outgoing channel: " + channel.getRemoteAddress(), e1);
                } catch (IOException e2) {
                    // ignore
                }
            }
        }
    }
}
