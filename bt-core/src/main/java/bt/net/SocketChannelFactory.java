package bt.net;

import bt.service.INetworkService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * Provides the basic capabilities
 * for establishing inbound and outbound connections.
 *
 * @since 1.0
 */
class SocketChannelFactory {

    private INetworkService networkService;
    private SelectorProvider selector;
    private volatile ServerSocketChannel incomingChannel;
    private final Object lock;

    /**
     * @since 1.0
     */
    public SocketChannelFactory(INetworkService networkService) {
        this.networkService = networkService;
        this.selector = SelectorProvider.provider();
        this.lock = new Object();
    }

    /**
     * Opens and connects a non-blocking socket channel.
     * Used to create outbound connections.
     *
     * @since 1.0
     */
    public SocketChannel getChannel(InetAddress inetAddress, int port) throws IOException {

        InetSocketAddress remoteAddress = new InetSocketAddress(inetAddress, port);
        SocketChannel outgoingChannel = selector.openSocketChannel();
        outgoingChannel.connect(remoteAddress);
        outgoingChannel.configureBlocking(false);
        return outgoingChannel;
    }

    /**
     * @return A blocking inbound channel,
     *         that can be used to listen for incoming connections.
     * @since 1.0
     */
    public ServerSocketChannel getIncomingChannel() throws IOException {

        if (incomingChannel == null) {
            synchronized (lock) {
                if (incomingChannel == null) {
                    SocketAddress localAddress = new InetSocketAddress(
                            networkService.getInetAddress(), networkService.getPort());
                    ServerSocketChannel serverSocketChannel = selector.openServerSocketChannel();
                    serverSocketChannel.bind(localAddress);
                    this.incomingChannel = serverSocketChannel;
                }
            }
        }
        return incomingChannel;
    }
}
