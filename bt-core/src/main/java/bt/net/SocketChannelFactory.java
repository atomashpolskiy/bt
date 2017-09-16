/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;

/**
 * Provides the basic capabilities
 * for establishing inbound and outbound connections.
 */
class SocketChannelFactory {

    // TODO: this should be configurable
    private static final Duration socketTimeout = Duration.ofSeconds(30);

    private InetSocketAddress localOutgoingSocketAddress;
    private InetAddress localIncomingAddress;
    private int localIncomingPort;

    private SelectorProvider selector;
    private volatile ServerSocketChannel incomingChannel;
    private final Object lock;

    /**
     * @since 1.0
     */
    public SocketChannelFactory(Selector selector, InetAddress localAddress, int localPort) {
        this.localOutgoingSocketAddress = new InetSocketAddress(localAddress, 0);
        this.localIncomingAddress = localAddress;
        this.localIncomingPort = localPort;
        this.selector = selector.provider();
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
        outgoingChannel.socket().bind(localOutgoingSocketAddress);
        outgoingChannel.socket().setSoTimeout((int) socketTimeout.toMillis());
        outgoingChannel.socket().setSoLinger(false, 0);
        outgoingChannel.connect(remoteAddress);
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
                    SocketAddress localAddress = new InetSocketAddress(localIncomingAddress, localIncomingPort);
                    ServerSocketChannel serverSocketChannel = selector.openServerSocketChannel();
                    serverSocketChannel.bind(localAddress);
                    this.incomingChannel = serverSocketChannel;
                }
            }
        }
        return incomingChannel;
    }
}
