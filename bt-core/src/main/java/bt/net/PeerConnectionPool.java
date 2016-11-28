package bt.net;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.service.IRuntimeLifecycleBinder;
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

public class PeerConnectionPool implements IPeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private PeerConnectionFactory connectionFactory;
    private IConnectionHandlerFactory connectionHandlerFactory;

    private ExecutorService executor;
    private ScheduledExecutorService cleaner;
    private ScheduledExecutorService connectionRequestor;

    private final Map<Peer, CompletableFuture<IPeerConnection>> pendingConnections;
    private ConcurrentMap<Peer, PeerConnection> connections;
    private Set<PeerActivityListener> connectionListeners;
    private ReentrantReadWriteLock listenerLock;
    private ReentrantLock connectionLock;

    private Duration peerConnectionInactivityThreshold;

    public PeerConnectionPool(SocketChannelFactory socketChannelFactory,
                              PeerConnectionFactory connectionFactory,
                              IConnectionHandlerFactory connectionHandlerFactory,
                              IRuntimeLifecycleBinder lifecycleBinder,
                              Duration peerConnectionInactivityThreshold) {

        this.connectionFactory = connectionFactory;
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.peerConnectionInactivityThreshold = peerConnectionInactivityThreshold;

        pendingConnections = new ConcurrentHashMap<>();
        connections = new ConcurrentHashMap<>();
        connectionListeners = new HashSet<>();
        listenerLock = new ReentrantReadWriteLock();
        connectionLock = new ReentrantLock();

        IncomingAcceptor acceptor = new IncomingAcceptor(socketChannelFactory);
        ExecutorService incomingAcceptor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "PeerConnectionPool-IncomingAcceptor"));
        lifecycleBinder.onStartup(() -> incomingAcceptor.execute(acceptor));

        cleaner = Executors.newScheduledThreadPool(1, r -> new Thread(r, "PeerConnectionPool-Cleaner"));
        lifecycleBinder.onStartup(() -> cleaner.scheduleAtFixedRate(new Cleaner(), 1000, 1000, TimeUnit.MILLISECONDS));

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

        connectionRequestor = Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(r, "TorrentSession-ConnectionRequestor"));

        lifecycleBinder.onShutdown(this.getClass().getName(), connectionRequestor::shutdownNow);
        lifecycleBinder.onShutdown(this.getClass().getName(), acceptor::shutdown);
        lifecycleBinder.onShutdown(this.getClass().getName(), incomingAcceptor::shutdownNow);
        lifecycleBinder.onShutdown(this.getClass().getName(), executor::shutdownNow);
        lifecycleBinder.onShutdown(this.getClass().getName(), this::shutdown);
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
    public IPeerConnection getConnection(Peer peer) {
        return connections.get(peer);
    }

    @Override
    public CompletableFuture<IPeerConnection> requestConnection(TorrentId torrentId, Peer peer) {

        CompletableFuture<IPeerConnection> connection = getExistingOrPendingConnection(peer);
        if (connection != null) {
            return connection;
        }

        synchronized (pendingConnections) {
            connection = getExistingOrPendingConnection(peer);
            if (connection != null) {
                return connection;
            }

            ConnectionHandler connectionHandler = connectionHandlerFactory.getOutgoingHandler(torrentId);
            connection = CompletableFuture.supplyAsync(() -> {
                try {
                    PeerConnection newConnection = connectionFactory.createConnection(peer);

                    if (!initConnection(newConnection, connectionHandler, true)) {
                        throw new BtException("Failed to initialize new connection for peer: " + peer);
                    }
                    return (IPeerConnection) connections.get(newConnection.getRemotePeer());
                } catch (IOException e) {
                    throw new BtException("Failed to create new outgoing connection for peer: " + peer, e);
                } finally {
                    synchronized (pendingConnections) {
                        pendingConnections.remove(peer);
                    }
                }
            }, executor).whenComplete((acquiredConnection, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("Failed to connect to peer: " + peer + "; will retry in 5 minutes", throwable);
                    connectionRequestor.schedule(() -> requestConnection(torrentId, peer), 5, TimeUnit.MINUTES);
                }
            });

            pendingConnections.put(peer, connection);
            return connection;

        }
    }

    private CompletableFuture<IPeerConnection> getExistingOrPendingConnection(Peer peer) {

        IPeerConnection existingConnection = getConnection(peer);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(existingConnection);
        }

        CompletableFuture<IPeerConnection> pendingConnection = pendingConnections.get(peer);
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
                    acceptIncomingConnection(serverChannel.accept());
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error when listening to the incoming channel @ " + localAddress, e);
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
                Iterator<PeerConnection> iter = connections.values().iterator();
                while (iter.hasNext()) {

                    PeerConnection connection = iter.next();
                    if (connection.isClosed()) {
                        purgeConnection(connection);
                        iter.remove();

                    } else if (System.currentTimeMillis() - connection.getLastActive()
                            >= peerConnectionInactivityThreshold.toMillis()) {

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Removing inactive peer connection: " + connection.getRemotePeer());
                        }
                        connection.closeQuietly();
                        purgeConnection(connection);
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
                PeerConnection incomingConnection = connectionFactory.createConnection(incomingChannel);
                initConnection(incomingConnection, connectionHandlerFactory.getIncomingHandler(), true);
            } catch (IOException e) {
                LOGGER.error("Failed to process incoming connection", e);
            }
        });
    }

    private boolean initConnection(PeerConnection newConnection, ConnectionHandler connectionHandler, boolean shouldNotifyListeners) {
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

    private boolean addConnection(PeerConnection newConnection, boolean shouldNotifyListeners) {

        boolean added = true;

        connectionLock.lock();
        PeerConnection existingConnection;
        try {
            existingConnection = connections.putIfAbsent(newConnection.getRemotePeer(), newConnection);
        } finally {
            connectionLock.unlock();
        }
        if (existingConnection != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Connection already exists for peer: " + newConnection.getRemotePeer());
            }
            newConnection.closeQuietly();
            newConnection = existingConnection;
            added = false;
        }

        if (added && shouldNotifyListeners) {
            // TODO: is locking still needed here?
            listenerLock.readLock().lock();
            try {
                for (PeerActivityListener listener : connectionListeners) {
                    listener.onPeerConnected(newConnection.getTorrentId(), newConnection.getRemotePeer());
                }
            } finally {
                listenerLock.readLock().unlock();
            }
        }
        return added;
    }

    private void purgeConnection(PeerConnection connection) {
        PeerConnection purged = connections.remove(connection.getRemotePeer());
        if (purged != null) {
            for (PeerActivityListener listener : connectionListeners) {
                listener.onPeerDisconnected(connection.getRemotePeer());
            }
        }
    }

    private void shutdown() {
        shutdownCleaner();
        connections.values().forEach(PeerConnection::closeQuietly);
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
