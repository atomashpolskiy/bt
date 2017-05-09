package bt.net;

import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.Objects;

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
}
