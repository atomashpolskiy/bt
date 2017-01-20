package bt.net;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.module.BitTorrentProtocol;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerConnectionPool implements IPeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private Config config;
    private PeerConnectionFactory connectionFactory;
    private IConnectionHandlerFactory connectionHandlerFactory;

    private ExecutorService executor;
    private ScheduledExecutorService cleaner;

    private final Map<Peer, CompletableFuture<PeerConnection>> pendingConnections;
    private ConcurrentMap<Peer, DefaultPeerConnection> connections;
    private Set<PeerActivityListener> connectionListeners;
    private ReentrantReadWriteLock listenerLock;
    private ReentrantLock connectionLock;

    private Duration peerConnectionInactivityThreshold;

    @Inject
    public PeerConnectionPool(@BitTorrentProtocol MessageHandler<Message> messageHandler,
                              IConnectionHandlerFactory connectionHandlerFactory,
                              IRuntimeLifecycleBinder lifecycleBinder,
                              Config config) {

        this.config = config;

        SocketChannelFactory socketChannelFactory =
                new SocketChannelFactory(config.getAcceptorAddress(), config.getAcceptorPort());
        this.connectionFactory = new PeerConnectionFactory(messageHandler,
                socketChannelFactory, config.getMaxTransferBlockSize());

        this.connectionHandlerFactory = connectionHandlerFactory;
        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();

        this.pendingConnections = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.connectionListeners = new HashSet<>();
        this.listenerLock = new ReentrantReadWriteLock();
        this.connectionLock = new ReentrantLock();

        IncomingAcceptor acceptor = new IncomingAcceptor(socketChannelFactory);
        ExecutorService incomingAcceptor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "bt.net.pool.incoming-acceptor"));
        lifecycleBinder.onStartup("Initialize incoming connection acceptor", () -> incomingAcceptor.execute(acceptor));

        this.cleaner = Executors.newScheduledThreadPool(1, r -> new Thread(r, "bt.net.pool.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of stale peer connections",
                () -> cleaner.scheduleAtFixedRate(new Cleaner(), 1000, 1000, TimeUnit.MILLISECONDS));

        this.executor = Executors.newCachedThreadPool(
                new ThreadFactory() {

                    private AtomicInteger threadCount = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "bt.net.pool.connection-worker-" + threadCount.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                }
        );

        lifecycleBinder.onShutdown("Shutdown incoming connection acceptor", () -> {
            try {
                acceptor.shutdown();
            } finally {
                incomingAcceptor.shutdownNow();
            }
        });
        lifecycleBinder.onShutdown("Shutdown outgoing connection request processor", executor::shutdownNow);
        lifecycleBinder.onShutdown("Shutdown connection pool", this::shutdown);
    }

    @Override
    public void addConnectionListener(PeerActivityListener listener) {
        listenerLock.writeLock().lock();
        try {
            connectionListeners.add(listener);
        } finally {
            listenerLock.writeLock().unlock();
        }
    }

    @Override
    public PeerConnection getConnection(Peer peer) {
        return connections.get(peer);
    }

    @Override
    public CompletableFuture<PeerConnection> requestConnection(TorrentId torrentId, Peer peer) {
        CompletableFuture<PeerConnection> connection = getExistingOrPendingConnection(peer);
        if (connection != null) {
            return connection;
        }

        if (connections.size() >= config.getMaxPeerConnections()) {
            // seems a bit like a hack. just a little bit
            return CompletableFuture.supplyAsync(() -> {throw new BtException("Connections limit exceeded");}, executor);
        }

        synchronized (pendingConnections) {
            connection = getExistingOrPendingConnection(peer);
            if (connection != null) {
                return connection;
            }

            ConnectionHandler connectionHandler = connectionHandlerFactory.getOutgoingHandler(torrentId);
            connection = CompletableFuture.supplyAsync(() -> {
                try {
                    DefaultPeerConnection newConnection = connectionFactory.createConnection(peer);

                    if (!initConnection(newConnection, connectionHandler, true)) {
                        throw new BtException("Failed to initialize new connection for peer: " + peer);
                    }
                    return (PeerConnection) connections.get(newConnection.getRemotePeer());
                } catch (IOException e) {
                    throw new BtException("Failed to create new outgoing connection for peer: " + peer, e);
                } finally {
                    synchronized (pendingConnections) {
                        pendingConnections.remove(peer);
                    }
                }
            }, executor).whenComplete((acquiredConnection, throwable) -> {
                if (throwable != null) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Failed to connect to peer: " + peer, throwable);
                    }
                }
            });

            pendingConnections.put(peer, connection);
            return connection;

        }
    }

    private CompletableFuture<PeerConnection> getExistingOrPendingConnection(Peer peer) {

        PeerConnection existingConnection = getConnection(peer);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(existingConnection);
        }

        CompletableFuture<PeerConnection> pendingConnection = pendingConnections.get(peer);
        if (pendingConnection != null) {
            return pendingConnection;
        }

        return null;
    }

    private class IncomingAcceptor implements Runnable {

        private SocketChannelFactory socketChannelFactory;
        private ServerSocketChannel serverChannel;

        private volatile boolean shutdown;

        IncomingAcceptor(SocketChannelFactory socketChannelFactory) {
            this.socketChannelFactory = socketChannelFactory;
        }

        @Override
        public void run() {
            SocketAddress localAddress;
            try {
                serverChannel = socketChannelFactory.getIncomingChannel();
                localAddress = serverChannel.getLocalAddress();
                LOGGER.info("Opening server channel for incoming connections @ " + localAddress);
            } catch (IOException e) {
                throw new BtException("Failed to create incoming connections listener " +
                        "-- unexpected I/O exception happened when creating an incoming channel", e);
            }

            try {
                while (!shutdown) {
                    if (connections.size() < config.getMaxPeerConnections()) {
                        SocketChannel channel = serverChannel.accept();
                        if (connections.size() < config.getMaxPeerConnections()) {
                            acceptIncomingConnection(channel);
                        } else {
                            rejectIncomingConnection(channel);
                        }
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Unexpectedly interrupted", e);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error when listening to the incoming channel @ " + localAddress, e);
            }
        }

        private void rejectIncomingConnection(SocketChannel channel) {
            try {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rejected incoming connection from {} due to exceeding of connections limit",
                            channel.getRemoteAddress());
                }
                channel.close();
            } catch (IOException e) {
                LOGGER.warn("Unexpected I/O error when rejecting incoming connection", e);
            }
        }

        public void shutdown() {
            shutdown = true;
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Closing the incoming channel...");
                }
                if (serverChannel != null) {
                    serverChannel.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to close the incoming channel", e);
            }
        }
    }

    private class Cleaner implements Runnable {

        @Override
        public void run() {

            if (connections.isEmpty()) {
                return;
            }

            connectionLock.lock();
            try {
                Iterator<DefaultPeerConnection> iter = connections.values().iterator();
                while (iter.hasNext()) {

                    DefaultPeerConnection connection = iter.next();
                    if (connection.isClosed()) {
                        purgeConnectionWithPeer(connection.getRemotePeer());
                        iter.remove();

                    } else if (System.currentTimeMillis() - connection.getLastActive()
                            >= peerConnectionInactivityThreshold.toMillis()) {

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Removing inactive peer connection: " + connection.getRemotePeer());
                        }
                        purgeConnectionWithPeer(connection.getRemotePeer());
                        iter.remove();
                    }
                    // can send keep-alives here based on lastActiveTime
                }
            } finally {
                connectionLock.unlock();
            }
        }
    }

    private void acceptIncomingConnection(SocketChannel incomingChannel) {
        executor.execute(() -> {
            try {
                incomingChannel.configureBlocking(false);
                DefaultPeerConnection incomingConnection = connectionFactory.createConnection(incomingChannel);
                initConnection(incomingConnection, connectionHandlerFactory.getIncomingHandler(), true);
            } catch (IOException e) {
                LOGGER.error("Failed to process incoming connection", e);
            }
        });
    }

    private boolean initConnection(DefaultPeerConnection newConnection, ConnectionHandler connectionHandler, boolean shouldNotifyListeners) {
        boolean success = connectionHandler.handleConnection(newConnection);
        if (success) {
            success = addConnection(newConnection, shouldNotifyListeners);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Successfully performed handshake for connection, remote peer: " +
                        newConnection.getRemotePeer() + "; handshake handler: " + connectionHandler.getClass().getName());
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to perform handshake for connection, remote peer: " +
                        newConnection.getRemotePeer() + "; handshake handler: " + connectionHandler.getClass().getName());
            }
            newConnection.closeQuietly();
        }
        return success;
    }

    private boolean addConnection(DefaultPeerConnection newConnection, boolean shouldNotifyListeners) {

        boolean added = false;
        DefaultPeerConnection existingConnection = null;

        connectionLock.lock();
        try {
            if (connections.size() >= config.getMaxPeerConnections()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Closing newly created connection with {} due to exceeding of connections limit",
                            newConnection.getRemotePeer());
                }
                newConnection.closeQuietly();
            } else {
                existingConnection = connections.putIfAbsent(newConnection.getRemotePeer(), newConnection);
                added = (existingConnection == null);
            }
        } finally {
            connectionLock.unlock();
        }
        if (existingConnection != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Connection already exists for peer: " + newConnection.getRemotePeer());
            }
            newConnection.closeQuietly();
            newConnection = existingConnection;
        }

        if (added && shouldNotifyListeners) {
            // TODO: is locking still needed here?
            listenerLock.readLock().lock();
            try {
                for (PeerActivityListener listener : connectionListeners) {
                    try {
                        listener.onPeerConnected(newConnection.getTorrentId(), newConnection.getRemotePeer());
                    } catch (Exception e) {
                        // ignore
                    }
                }
            } finally {
                listenerLock.readLock().unlock();
            }
        }
        return added;
    }

    private void purgeConnectionWithPeer(Peer peer) {
        DefaultPeerConnection purged = connections.remove(peer);
        if (purged != null) {
            if (!purged.isClosed()) {
                purged.closeQuietly();
            }
            for (PeerActivityListener listener : connectionListeners) {
                try {
                    listener.onPeerDisconnected(purged.getTorrentId(), peer);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private void shutdown() {
        shutdownCleaner();
        connections.values().forEach(DefaultPeerConnection::closeQuietly);
    }

    private void shutdownCleaner() {
        cleaner.shutdown();
        try {
            cleaner.awaitTermination(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!cleaner.isShutdown()) {
            cleaner.shutdownNow();
        }
    }
}
