package bt.net;

import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class PeerConnectionFactory {

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

    public PeerConnection createConnection(SocketChannel channel) throws IOException {

        Peer peer = getPeerForAddress((InetSocketAddress) channel.getRemoteAddress());
        return new PeerConnection(messageHandler, peer, channel, maxTransferBlockSize);
    }

    private Peer getPeerForAddress(InetSocketAddress address) {
        return new InetPeer(address.getAddress(), address.getPort());
    }

    public PeerConnection createConnection(Peer peer) throws IOException {

        if (peer == null) {
            throw new NullPointerException("Peer is null");
        }

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel;
        try {
            channel = socketChannelFactory.getChannel(inetAddress, port);
        } catch (IOException e) {
            throw new IOException("Failed to create peer connection @ " + inetAddress + ":" + port, e);
        }

        return new PeerConnection(messageHandler, peer, channel, maxTransferBlockSize);
    }
}
