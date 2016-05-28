package bt.net;

import bt.BtException;
import bt.service.IConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class PeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private PeerConnectionFactory connectionFactory;

    private ExecutorService incomingAcceptor;
    private HandshakeHandler incomingHandshakeHandler;

    private Executor executor;

    private ConcurrentMap<Peer, PeerConnection> connections;

    public PeerConnectionPool(PeerConnectionFactory connectionFactory, SocketChannelFactory socketChannelFactory,
                              HandshakeHandler incomingHandshakeHandler, IConfigurationService configurationService) {

        this.connectionFactory = connectionFactory;
        this.incomingHandshakeHandler = incomingHandshakeHandler;

        connections = new ConcurrentHashMap<>();

        this.incomingAcceptor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "PeerConnectionPool-IncomingAcceptor"));
        incomingAcceptor.execute(new IncomingAcceptor(socketChannelFactory));

        executor = Executors.newCachedThreadPool(
                new ThreadFactory() {

                    private AtomicInteger threadCount = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "PeerConnectionPool-Worker[" + threadCount.getAndIncrement() + "]");
                        t.setDaemon(true);
                        return t;
                    }
                }
        );
    }

    public PeerConnection requestConnection(Peer peer, HandshakeHandler handshakeHandler) {
        PeerConnection existingConnection = connections.get(peer);
        if (existingConnection == null) {
            executor.execute(() -> {
                try {
                    PeerConnection newConnection = connectionFactory.createConnection(peer);
                    initConnection(newConnection, handshakeHandler);
                } catch (IOException e) {
                    LOGGER.error("Failed to create new outgoing connection for peer: " + peer, e);
                }
            });
        }
        return existingConnection;
    }

    private class IncomingAcceptor implements Runnable {

        private ServerSocketChannel serverChannel;
        private SocketAddress localAddress;

        IncomingAcceptor(SocketChannelFactory socketChannelFactory) {
            try {
                serverChannel = socketChannelFactory.getIncomingChannel();
                this.localAddress = serverChannel.getLocalAddress();
                LOGGER.info("Opened server channel for incoming connections @ " + localAddress);
            } catch (IOException e) {
                throw new BtException("Failed to create incoming connections listener " +
                        "-- unexpected I/O exception happened when creating an incoming channel", e);
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    acceptIncomingConnection(serverChannel.accept());
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error when listening to the incoming channel: " + localAddress, e);
                try {
                    serverChannel.close();
                } catch (IOException e1) {
                    LOGGER.warn("Failed to close the incoming channel", e);
                }
            }
        }
    }

    private void acceptIncomingConnection(SocketChannel incomingChannel) {
        executor.execute(() -> {
            try {
                PeerConnection incomingConnection = connectionFactory.createConnection(incomingChannel);
                initConnection(incomingConnection, incomingHandshakeHandler);
            } catch (IOException e) {
                LOGGER.error("Failed to process incoming connection", e);
            }
        });
    }

    private void initConnection(PeerConnection newConnection, HandshakeHandler handshakeHandler) {
        if (handshakeHandler.handleConnection(newConnection)) {
            addConnection(newConnection);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Successfully performed handshake for connection, remote peer: " +
                        newConnection.getRemotePeer() + "; handshake handler: " + handshakeHandler.getClass().getName());
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to perform handshake for connection, remote peer: " +
                        newConnection.getRemotePeer() + "; handshake handler: " + handshakeHandler.getClass().getName());
            }
            newConnection.closeQuietly();
        }
    }

    private void addConnection(PeerConnection newConnection) {

        PeerConnection existingConnection = connections.putIfAbsent(newConnection.getRemotePeer(), newConnection);
        if (existingConnection != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Connection already exists for peer: " + newConnection.getRemotePeer());
            }
            newConnection.closeQuietly();
        }
    }
}
