package bt.net;

import bt.BtException;
import bt.event.EventSink;
import bt.metainfo.TorrentId;
import bt.module.BitTorrentProtocol;
import bt.module.PeerConnectionSelector;
import bt.peer.IPeerRegistry;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
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

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerConnectionPool implements IPeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private Config config;
    private IPeerRegistry peerRegistry;
    private PeerConnectionFactory connectionFactory;
    private IConnectionHandlerFactory connectionHandlerFactory;
    private EventSink eventSink;

    private ExecutorService executor;
    private ScheduledExecutorService cleaner;

    private final Map<Peer, CompletableFuture<Optional<PeerConnection>>> pendingConnections;
    private ConcurrentMap<Peer, DefaultPeerConnection> connections;
    private ReentrantLock cleanerLock;

    private Map<Peer, Long> unreachablePeers;

    private Duration peerConnectionInactivityThreshold;

    @Inject
    public PeerConnectionPool(@BitTorrentProtocol MessageHandler<Message> messageHandler,
                              IPeerRegistry peerRegistry,
                              TorrentRegistry torrentRegistry,
                              EventSink eventSink,
                              @PeerConnectionSelector SharedSelector selector,
                              IConnectionHandlerFactory connectionHandlerFactory,
                              IRuntimeLifecycleBinder lifecycleBinder,
                              Config config) {

        this.config = config;
        this.peerRegistry = peerRegistry;
        this.eventSink = eventSink;

        SocketChannelFactory socketChannelFactory =
                new SocketChannelFactory(selector, config.getAcceptorAddress(), config.getAcceptorPort());
        this.connectionFactory = new PeerConnectionFactory(messageHandler, socketChannelFactory, torrentRegistry, selector, config);

        this.connectionHandlerFactory = connectionHandlerFactory;
        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();

        this.pendingConnections = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.cleanerLock = new ReentrantLock();

        this.unreachablePeers = new ConcurrentHashMap<>();

        IncomingAcceptor acceptor = new IncomingAcceptor(socketChannelFactory);
        ExecutorService incomingAcceptor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "bt.net.pool.incoming-acceptor"));
        lifecycleBinder.onStartup("Initialize incoming connection acceptor", () -> incomingAcceptor.execute(acceptor));

        this.cleaner = Executors.newScheduledThreadPool(1, r -> new Thread(r, "bt.net.pool.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of stale peer connections",
                () -> cleaner.scheduleAtFixedRate(new Cleaner(config.getUnreachablePeerBanDuration()), 1000, 1000, TimeUnit.MILLISECONDS));

        this.executor = Executors.newFixedThreadPool(config.getMaxPendingConnectionRequests(),
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
    public PeerConnection getConnection(Peer peer) {
        return connections.get(peer);
    }

    @Override
    public CompletableFuture<Optional<PeerConnection>> requestConnection(TorrentId torrentId, Peer peer) {
        CompletableFuture<Optional<PeerConnection>> connection = getExistingOrPendingConnection(peer);
        if (connection != null) {
            return connection;
        }

        if (unreachablePeers.containsKey(peer)) {
            return CompletableFuture.completedFuture(Optional.empty());
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
                    DefaultPeerConnection newConnection = connectionFactory.createOutgoingConnection(peer, torrentId);

                    if (!initConnection(newConnection, connectionHandler, true)) {
                        throw new BtException("Failed to initialize new connection for peer: " + peer);
                    }
                    return Optional.of((PeerConnection) connections.get(newConnection.getRemotePeer()));
                } catch (IOException e) {
                    throw new BtException("Failed to create new outgoing connection for peer: " + peer, e);
                } finally {
                    synchronized (pendingConnections) {
                        pendingConnections.remove(peer);
                    }
                }
            }, executor).whenComplete((acquiredConnection, throwable) -> {
                if (throwable != null) {
                    cleanerLock.lock();
                    try {
                        unreachablePeers.putIfAbsent(peer, System.currentTimeMillis());
                    } finally {
                        cleanerLock.unlock();
                    }
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Failed to connect to peer: " + peer, throwable);
                    }
                }
            });

            pendingConnections.put(peer, connection);
            return connection;

        }
    }

    private CompletableFuture<Optional<PeerConnection>> getExistingOrPendingConnection(Peer peer) {

        PeerConnection existingConnection = getConnection(peer);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(Optional.of(existingConnection));
        }

        CompletableFuture<Optional<PeerConnection>> pendingConnection = pendingConnections.get(peer);
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

        private final Duration unreachablePeerBanDuration;

        Cleaner(Duration unreachablePeerBanDuration) {
            this.unreachablePeerBanDuration = unreachablePeerBanDuration;
        }

        @Override
        public void run() {

            if (connections.isEmpty()) {
                return;
            }

            cleanerLock.lock();
            try {
                {
                    Iterator<DefaultPeerConnection> iter = connections.values().iterator();
                    while (iter.hasNext()) {

                        DefaultPeerConnection connection = iter.next();
                        if (connection.isClosed()) {
                            purgeConnectionWithPeer(connection.getRemotePeer());
                            iter.remove();

                        } else if (System.currentTimeMillis() - connection.getLastActive()
                                >= peerConnectionInactivityThreshold.toMillis()) {

                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Removing inactive peer connection: {}", connection.getRemotePeer());
                            }
                            purgeConnectionWithPeer(connection.getRemotePeer());
                            iter.remove();
                        }
                        // can send keep-alives here based on lastActiveTime
                    }
                }

                {
                    Iterator<Map.Entry<Peer, Long>> iter = unreachablePeers.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Peer, Long> entry = iter.next();
                        if (System.currentTimeMillis() - entry.getValue() >= unreachablePeerBanDuration.toMillis()) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Removing temporary ban for unreachable peer: {}", entry.getKey());
                            }
                            iter.remove();
                        }
                    }
                }
            } finally {
                cleanerLock.unlock();
            }
        }
    }

    private void acceptIncomingConnection(SocketChannel incomingChannel) {
        executor.execute(() -> {
            try {
                Peer peer = peerRegistry.getPeerForAddress((InetSocketAddress) incomingChannel.getRemoteAddress());
                DefaultPeerConnection incomingConnection = connectionFactory.createIncomingConnection(peer, incomingChannel);
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

        cleanerLock.lock();
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
            cleanerLock.unlock();
        }
        if (existingConnection != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Connection already exists for peer: " + newConnection.getRemotePeer());
            }
            newConnection.closeQuietly();
            newConnection = existingConnection;
        }

        if (added && shouldNotifyListeners) {
            eventSink.firePeerConnected(newConnection.getTorrentId(), newConnection.getRemotePeer());
        }
        return added;
    }

    private void purgeConnectionWithPeer(Peer peer) {
        DefaultPeerConnection purged = connections.remove(peer);
        if (purged != null) {
            if (!purged.isClosed()) {
                purged.closeQuietly();
            }
            eventSink.firePeerDisconnected(purged.getTorrentId(), peer);
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
