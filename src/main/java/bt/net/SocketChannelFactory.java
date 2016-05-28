package bt.net;

import bt.service.INetworkService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

public class SocketChannelFactory {

    private INetworkService networkService;
    private final SelectorProvider selector;

    public SocketChannelFactory(INetworkService networkService) {
        this.networkService = networkService;
        selector = SelectorProvider.provider();
    }

    public SocketChannel getChannel(InetAddress inetAddress, int port) throws IOException {

        InetSocketAddress remoteAddress = new InetSocketAddress(inetAddress, port);
        SocketChannel outgoingChannel = selector.openSocketChannel();
        outgoingChannel.connect(remoteAddress);
        return outgoingChannel;
    }

    public ServerSocketChannel getIncomingChannel() throws IOException {

        SocketAddress localAddress = new InetSocketAddress(networkService.getInetAddress(), networkService.getPort());
        ServerSocketChannel serverSocketChannel = selector.openServerSocketChannel();
        serverSocketChannel.bind(localAddress);
        return serverSocketChannel;
    }
}
