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

import bt.event.EventSource;
import bt.metainfo.TorrentId;
import bt.net.buffer.BorrowedBuffer;
import bt.net.buffer.IBufferManager;
import bt.net.crypto.CipherBufferMutator;
import bt.net.crypto.MSEHandshakeProcessor;
import bt.net.pipeline.ChannelHandler;
import bt.net.pipeline.ChannelPipeline;
import bt.net.pipeline.ChannelPipelineBuilder;
import bt.net.pipeline.IChannelPipelineFactory;
import bt.net.pipeline.SocketChannelHandler;
import bt.protocol.Message;
import bt.protocol.crypto.MSECipher;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.torrent.TorrentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public class PeerConnectionFactory implements IPeerConnectionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionFactory.class);

    private static final Duration socketTimeout = Duration.ofSeconds(30);

    private MessageHandler<Message> protocol;

    private Selector selector;
    private IConnectionHandlerFactory connectionHandlerFactory;
    private IChannelPipelineFactory channelPipelineFactory;
    private IBufferManager bufferManager;
    private MSEHandshakeProcessor cryptoHandshakeProcessor;
    private DataReceiver dataReceiver;
    private EventSource eventSource;

    private InetSocketAddress localOutgoingSocketAddress;

    public PeerConnectionFactory(Selector selector,
                                 IConnectionHandlerFactory connectionHandlerFactory,
                                 IChannelPipelineFactory channelPipelineFactory,
                                 MessageHandler<Message> protocol,
                                 TorrentRegistry torrentRegistry,
                                 IBufferManager bufferManager,
                                 DataReceiver dataReceiver,
                                 EventSource eventSource,
                                 Config config) {

        this.protocol = protocol;
        this.selector = selector;
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.channelPipelineFactory = channelPipelineFactory;
        this.bufferManager = bufferManager;
        this.cryptoHandshakeProcessor = new MSEHandshakeProcessor(torrentRegistry, protocol, config);
        this.dataReceiver = dataReceiver;
        this.eventSource = eventSource;
        this.localOutgoingSocketAddress = new InetSocketAddress(config.getAcceptorAddress(), 0);
    }

    @Override
    public ConnectionResult createOutgoingConnection(Peer peer, TorrentId torrentId) {
        Objects.requireNonNull(peer);
        Objects.requireNonNull(torrentId);

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel;
        try {
            channel = getChannel(inetAddress, port);
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to establish connection with peer: {}. Reason: {} ({})",
                        peer, e.getClass().getName(), e.getMessage());
            }
            return ConnectionResult.failure("I/O error", e);
        }

        return createConnection(peer, torrentId, channel, false);
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
        return createConnection(peer, null, channel, true);
    }

    private ConnectionResult createConnection(Peer peer, TorrentId torrentId, SocketChannel channel, boolean incoming) {
        BorrowedBuffer<ByteBuffer> in = bufferManager.borrowByteBuffer();
        BorrowedBuffer<ByteBuffer> out = bufferManager.borrowByteBuffer();
        try {
            return _createConnection(peer, torrentId, channel, incoming, in, out);
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to establish connection with peer: {}. Reason: {} ({})",
                        peer, e.getClass().getName(), e.getMessage());
            }
            closeQuietly(channel);
            releaseBuffer(in);
            releaseBuffer(out);
            return ConnectionResult.failure("Unexpected error", e);
        }
    }

    private ConnectionResult _createConnection(
            Peer peer,
            TorrentId torrentId,
            SocketChannel channel,
            boolean incoming,
            BorrowedBuffer<ByteBuffer> in,
            BorrowedBuffer<ByteBuffer> out) throws IOException {

        // sanity check
        if (!incoming && torrentId == null) {
            throw new IllegalStateException("Requested outgoing connection without torrent ID. Peer: " + peer);
        }

        channel.configureBlocking(false);

        ByteBuffer inBuffer = in.lockAndGet();
        ByteBuffer outBuffer = out.lockAndGet();
        Optional<MSECipher> cipherOptional;
        try {
            if (incoming) {
                cipherOptional = cryptoHandshakeProcessor.negotiateIncoming(peer, channel, inBuffer, outBuffer);
            } else {
                cipherOptional = cryptoHandshakeProcessor.negotiateOutgoing(peer, channel, torrentId, inBuffer, outBuffer);
            }
        } finally {
            in.unlock();
            out.unlock();
        }

        ChannelPipeline pipeline = createPipeline(peer, channel, in, out, cipherOptional);
        ChannelHandler channelHandler = new SocketChannelHandler(channel, in, out, pipeline::bindHandler, dataReceiver);
        channelHandler.register();

        PeerConnection connection = new SocketPeerConnection(peer, channelHandler);
        ConnectionHandler connectionHandler;
        if (incoming) {
            connectionHandler = connectionHandlerFactory.getIncomingHandler();
        } else {
            connectionHandler = connectionHandlerFactory.getOutgoingHandler(torrentId);
        }
        boolean inited = initConnection(connection, connectionHandler);
        if (inited) {
            subscribeHandler(connection.getTorrentId(), channelHandler);
            return ConnectionResult.success(connection);
        } else {
            connection.closeQuietly();
            return ConnectionResult.failure("Handshake failed");
        }
    }

    private void subscribeHandler(TorrentId torrentId, ChannelHandler channelHandler) {
        eventSource.onTorrentStarted(event -> {
            if (event.getTorrentId().equals(torrentId)) {
                channelHandler.activate();
            }
        });
        eventSource.onTorrentStopped(event -> {
            if (event.getTorrentId().equals(torrentId)) {
                channelHandler.deactivate();
            }
        });
    }

    private ChannelPipeline createPipeline(
            Peer peer,
            ByteChannel channel,
            BorrowedBuffer<ByteBuffer> in,
            BorrowedBuffer<ByteBuffer> out,
            Optional<MSECipher> cipherOptional) {

        ChannelPipelineBuilder builder = channelPipelineFactory.buildPipeline(peer);
        builder.channel(channel);
        builder.protocol(protocol);
        builder.inboundBuffer(in);
        builder.outboundBuffer(out);

        cipherOptional.ifPresent(cipher -> {
            builder.decoders(new CipherBufferMutator(cipher.getDecryptionCipher()));
            builder.encoders(new CipherBufferMutator(cipher.getEncryptionCipher()));
        });

        return builder.build();
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

    private void releaseBuffer(BorrowedBuffer<ByteBuffer> buffer) {
        try {
            buffer.release();
        } catch (Exception e) {
            LOGGER.error("Failed to release buffer", e);
        }
    }
}
