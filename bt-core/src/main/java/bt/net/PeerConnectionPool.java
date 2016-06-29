package bt.net;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.Protocol;
import bt.service.INetworkService;
import bt.service.IPeerRegistry;
import bt.service.IShutdownService;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class PeerConnectionPool implements IPeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private PeerConnectionFactory connectionFactory;

    private ExecutorService incomingAcceptor;
    private HandshakeHandler incomingHandshakeHandler;

    private ExecutorService executor;

    private Map<Peer, CompletableFuture<IPeerConnection>> pendingConnections;
    private ConcurrentMap<Peer, ManagedPeerConnection> connections;
    private Set<Consumer<IPeerConnection>> connectionListeners;
    private ReentrantReadWriteLock listenerLock;
    private ReentrantLock connectionLock;

    @Inject
    public PeerConnectionPool(INetworkService networkService, Protocol protocol, IPeerRegistry peerRegistry,
                              IShutdownService shutdownService, IHandshakeHandlerFactory handshakeHandlerFactory) {

        SocketChannelFactory socketChannelFactory = new SocketChannelFactory(networkService);
        this.connectionFactory = new PeerConnectionFactory(protocol, peerRegistry, socketChannelFactory);

        this.incomingHandshakeHandler = handshakeHandlerFactory.getIncomingHandler();

        pendingConnections = new ConcurrentHashMap<>();
        connections = new ConcurrentHashMap<>();
        connectionListeners = new HashSet<>();
        listenerLock = new ReentrantReadWriteLock();
        connectionLock = new ReentrantLock();

        IncomingAcceptor acceptor = new IncomingAcceptor(socketChannelFactory);
        incomingAcceptor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "PeerConnectionPool-IncomingAcceptor"));
        incomingAcceptor.execute(acceptor);

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

        shutdownService.addShutdownHook(acceptor::shutdown);
        shutdownService.addShutdownHook(() -> incomingAcceptor.shutdownNow());
        shutdownService.addShutdownHook(() -> executor.shutdownNow());
        shutdownService.addShutdownHook(this::shutdown);
    }

    @Override
    public void addConnectionListener(Consumer<IPeerConnection> listener) {
        listenerLock.writeLock().lock();
        try {
            connectionListeners.add(listener);
        } finally {
            listenerLock.writeLock().unlock();
        }
    }

    @Override
    public void removeConnectionListener(Consumer<IPeerConnection> listener) {
        listenerLock.writeLock().lock();
        try {
            connectionListeners.remove(listener);
        } finally {
            listenerLock.writeLock().unlock();
        }
    }

    @Override
    public IPeerConnection getConnection(Peer peer) {
        return connections.get(peer);
    }

    @Override
    public CompletableFuture<IPeerConnection> requestConnection(Peer peer, HandshakeHandler handshakeHandler) {

        CompletableFuture<IPeerConnection> connection = getExistingOrPendingConnection(peer);
        if (connection != null) {
            return connection;
        }

        connectionLock.lock();
        try {
            connection = getExistingOrPendingConnection(peer);
            if (connection != null) {
                return connection;
            }

            connection = CompletableFuture.supplyAsync(() -> {
                try {
                    PeerConnection newConnection = connectionFactory.createConnection(peer);
                    if (!initConnection(newConnection, handshakeHandler, false)) {
                        throw new BtException("Failed to initialize new connection for peer: " + peer);
                    }
                    return connections.get(newConnection.getRemotePeer());
                } catch (IOException e) {
                    throw new BtException("Failed to create new outgoing connection for peer: " + peer, e);
                } finally {
                    connectionLock.lock();
                    try {
                        pendingConnections.remove(peer);
                    } finally {
                        connectionLock.unlock();
                    }
                }
            }, executor);

            pendingConnections.put(peer, connection);
            return connection;

        } finally {
            connectionLock.unlock();
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

        private ServerSocketChannel serverChannel;
        private SocketAddress localAddress;

        private volatile boolean shutdown;

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
                while (!shutdown) {
                    acceptIncomingConnection(serverChannel.accept());
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error when listening to the incoming channel: " + localAddress, e);
            }
        }

        public void shutdown() {
            shutdown = true;
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Closing the incoming channel...");
                }
                serverChannel.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close the incoming channel", e);
            }
        }
    }

    private void acceptIncomingConnection(SocketChannel incomingChannel) {
        executor.execute(() -> {
            try {
                incomingChannel.configureBlocking(false);
                PeerConnection incomingConnection = connectionFactory.createConnection(incomingChannel);
                initConnection(incomingConnection, incomingHandshakeHandler, true);
            } catch (IOException e) {
                LOGGER.error("Failed to process incoming connection", e);
            }
        });
    }

    private boolean initConnection(PeerConnection newConnection, HandshakeHandler handshakeHandler, boolean shouldNotifyListeners) {
        boolean success = handshakeHandler.handleConnection(newConnection);
        if (success) {
            success = addConnection(newConnection, shouldNotifyListeners);
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
        return success;
    }

    private boolean addConnection(PeerConnection connection, boolean shouldNotifyListeners) {

        boolean added = true;

        ManagedPeerConnection newConnection = new ManagedPeerConnection(connection);
        ManagedPeerConnection existingConnection = connections.putIfAbsent(connection.getRemotePeer(), newConnection);
        if (existingConnection != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Connection already exists for peer: " + connection.getRemotePeer());
            }
            connection.closeQuietly();
            newConnection = existingConnection;
            added = false;
        }

        if (added && shouldNotifyListeners) {
            listenerLock.readLock().lock();
            try {
                for (Consumer<IPeerConnection> listener : connectionListeners) {
                    listener.accept(newConnection);
                }
            } finally {
                listenerLock.readLock().unlock();
            }
        }
        return added;
    }

    private class ManagedPeerConnection implements IPeerConnection {

        private IPeerConnection delegate;

        ManagedPeerConnection(IPeerConnection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object getTag() {
            return delegate.getTag();
        }

        @Override
        public Message readMessageNow() {
            return delegate.readMessageNow();
        }

        @Override
        public Message readMessage(long timeout) {
            return delegate.readMessage(timeout);
        }

        @Override
        public void postMessage(Message message) {
            delegate.postMessage(message);
        }

        @Override
        public Peer getRemotePeer() {
            return delegate.getRemotePeer();
        }

        @Override
        public void closeQuietly() {
            purgeConnection();
            delegate.closeQuietly();
        }

        @Override
        public void close() throws IOException {
            purgeConnection();
            delegate.close();
        }

        private void purgeConnection() {
            ManagedPeerConnection purged = connections.remove(delegate.getRemotePeer());
            if (purged != null && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Removing peer connection: " + purged.getRemotePeer());
            }
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public long getLastActive() {
            return delegate.getLastActive();
        }
    }

    private void shutdown() {
        connections.values().forEach(ManagedPeerConnection::closeQuietly);
    }
}
