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

import bt.metainfo.TorrentId;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.torrent.TorrentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Objects;

public class PeerConnectionFactory implements IPeerConnectionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionFactory.class);

    private static final Duration socketTimeout = Duration.ofSeconds(30);

    private Selector selector;
    private IConnectionHandlerFactory connectionHandlerFactory;
    private MSEHandshakeProcessor cryptoHandshakeProcessor;

    private InetSocketAddress localOutgoingSocketAddress;

    public PeerConnectionFactory(Selector selector,
                                 IConnectionHandlerFactory connectionHandlerFactory,
                                 MessageHandler<Message> messageHandler,
                                 TorrentRegistry torrentRegistry,
                                 IBufferManager bufferManager,
                                 Config config) {
        this.selector = selector;
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.cryptoHandshakeProcessor = new MSEHandshakeProcessor(torrentRegistry, messageHandler, bufferManager, config);
        this.localOutgoingSocketAddress = new InetSocketAddress(config.getAcceptorAddress(), 0);
    }

    @Override
    public ConnectionResult createOutgoingConnection(Peer peer, TorrentId torrentId) {
        Objects.requireNonNull(peer);

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel = null;
        try {
            channel = getChannel(inetAddress, port);
            return createConnection(peer, torrentId, channel, false);
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to establish ougoing connection to peer: {}. Reason: {} ({})",
                        peer, e.getClass().getName(), e.getMessage());
            }
            closeQuietly(channel);
            return ConnectionResult.failure("I/O error", e);
        }
    }

    private SocketChannel getChannel(InetAddress inetAddress, int port) throws IOException {
        InetSocketAddress remoteAddress = new InetSocketAddress(inetAddress, port);
        SocketChannel outgoingChannel = selector.provider().openSocketChannel();
        outgoingChannel.socket().bind(localOutgoingSocketAddress);
        outgoingChannel.socket().setSoTimeout((int) socketTimeout.toMillis());
        outgoingChannel.socket().setSoLinger(false, 0);
        outgoingChannel.connect(remoteAddress);
        return outgoingChannel;
    }

    @Override
    public ConnectionResult createIncomingConnection(Peer peer, SocketChannel channel) {
        try {
            return createConnection(peer, null, channel, true);
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to establish incoming connection from peer: {}. Reason: {} ({})",
                        peer, e.getClass().getName(), e.getMessage());
            }
            closeQuietly(channel);
            return ConnectionResult.failure("I/O error", e);
        }
    }

    private ConnectionResult createConnection(Peer peer,
                                              TorrentId torrentId,
                                              SocketChannel channel,
                                              boolean incoming) throws IOException {
        // sanity check
        if (!incoming && torrentId == null) {
            throw new IllegalStateException("Requested outgoing connection without torrent ID. Peer: " + peer);
        }

        channel.configureBlocking(false);

        PeerConnectionMessageWorker readerWriter = incoming ?
                cryptoHandshakeProcessor.negotiateIncoming(peer, channel)
                : cryptoHandshakeProcessor.negotiateOutgoing(peer, channel, torrentId);

        PeerConnection connection = new SocketPeerConnection(peer, channel, readerWriter);
        ConnectionHandler connectionHandler;
        if (incoming) {
            connectionHandler = connectionHandlerFactory.getIncomingHandler();
        } else {
            connectionHandler = connectionHandlerFactory.getOutgoingHandler(torrentId);
        }
        boolean inited = initConnection(connection, connectionHandler);
        if (inited) {
            return ConnectionResult.success(connection);
        } else {
            connection.closeQuietly();
            return ConnectionResult.failure("Handshake failed");
        }
    }

    private boolean initConnection(PeerConnection newConnection, ConnectionHandler connectionHandler) {
        boolean success = connectionHandler.handleConnection(newConnection);
        if (success) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Successfully initialized newly established connection to peer: {}, handshake handler: {}",
                        newConnection.getRemotePeer(), connectionHandler.getClass().getName());
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to initialize newly established connection to peer: {}, handshake handler: {}",
                        newConnection.getRemotePeer(), connectionHandler.getClass().getName());
            }
        }
        return success;
    }

    private void closeQuietly(SocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e1) {
                try {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed to close outgoing channel: {}. Reason: {} ({})",
                                channel.getRemoteAddress(), e1.getClass().getName(), e1.getMessage());
                    }
                } catch (IOException e2) {
                    // ignore
                }
            }
        }
    }
}
