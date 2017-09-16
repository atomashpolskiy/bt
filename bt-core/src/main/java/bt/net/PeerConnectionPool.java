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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Collection;
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
import java.util.function.Consumer;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerConnectionPool implements IPeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private Config config;
    private IPeerRegistry peerRegistry;
    private PeerConnectionFactory connectionFactory;

    private EventSink eventSink;

    private ExecutorService executor;
    private ScheduledExecutorService cleaner;

    private Connections connections;
    private final Map<Peer, CompletableFuture<Optional<PeerConnection>>> pendingConnections;
    private ReentrantLock cleanerLock;

    private ConcurrentMap<Peer, Long> unreachablePeers;

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
        this.connectionFactory = new PeerConnectionFactory(messageHandler, socketChannelFactory,
                connectionHandlerFactory, torrentRegistry, config);

        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();

        this.connections = new Connections();
        this.pendingConnections = new ConcurrentHashMap<>();
        this.cleanerLock = new ReentrantLock();

        this.unreachablePeers = new ConcurrentHashMap<>();

        IncomingAcceptor acceptor = new IncomingAcceptor(socketChannelFactory);
        ExecutorService incomingAcceptor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "bt.net.pool.incoming-acceptor"));
        lifecycleBinder.onStartup("Initialize incoming connection acceptor", () -> incomingAcceptor.execute(acceptor));

        this.cleaner = Executors.newScheduledThreadPool(1, r -> new Thread(r, "bt.net.pool.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of stale peer connections",
                () -> cleaner.scheduleAtFixedRate(new Cleaner(config.getUnreachablePeerBanDuration()), 1, 1, TimeUnit.SECONDS));

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
        return connections.get(peer).orElse(null);
    }

    @Override
    public void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor) {
        connections.visitConnections(torrentId, visitor);
    }

    @Override
    public CompletableFuture<Optional<PeerConnection>> requestConnection(TorrentId torrentId, Peer peer) {
        CompletableFuture<Optional<PeerConnection>> connection = getExistingOrPendingConnection(peer);
        if (connection != null) {
            if (connection.isDone() && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Returning existing connection for peer: {}. Torrent: {}", peer, torrentId);
            }
            return connection;
        }

        if (unreachablePeers.containsKey(peer)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Will not attempt to establish connection to peer: {}. " +
                        "Reason: peer is unreachable. Torrent: {}", peer, torrentId);
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }

        if (connections.count() >= config.getMaxPeerConnections()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Will not attempt to establish connection to peer: {}. " +
                        "Reason: connections limit exceeded. Torrent: {}", peer, torrentId);
            }
            // seems a bit like a hack. just a little bit
            return CompletableFuture.supplyAsync(
                    () -> {throw new RuntimeException("Connections limit exceeded");}, executor);
        }

        synchronized (pendingConnections) {
            connection = getExistingOrPendingConnection(peer);
            if (connection != null) {
                if (connection.isDone() && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Returning existing connection for peer: {}. Torrent: {}", peer, torrentId);
                }
                return connection;
            }

            connection = CompletableFuture.supplyAsync(() -> {
                try {
                    Optional<PeerConnection> newConnection =
                            connectionFactory.createOutgoingConnection(peer, torrentId);
                    if (newConnection.isPresent()) {
                        return Optional.of(addOrGetExisting(newConnection.get()));
                    } else {
                        return newConnection; // empty optional
                    }
                } finally {
                    synchronized (pendingConnections) {
                        pendingConnections.remove(peer);
                    }
                }
            }, executor).whenComplete((acquiredConnection, throwable) -> {
                if (acquiredConnection == null || throwable != null) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Peer is unreachable: {}. Will prevent further attempts to establish connection.", peer);
                    }
                    cleanerLock.lock();
                    try {
                        unreachablePeers.putIfAbsent(peer, System.currentTimeMillis());
                    } finally {
                        cleanerLock.unlock();
                    }

                }
                if (throwable != null) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed to establish outgoing connection to peer: " + peer, throwable);
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
                throw new RuntimeException("Failed to create incoming connections listener " +
                        "-- unexpected I/O exception happened when creating an incoming channel", e);
            }

            try {
                while (!shutdown) {
                    if (connections.count() < config.getMaxPeerConnections()) {
                        SocketChannel channel = serverChannel.accept();
                        if (connections.count() < config.getMaxPeerConnections()) {
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
            } catch (ClosedChannelException e) {
                LOGGER.info("Incoming channel @ {} has been closed, will stop accepting incoming connections...", localAddress);
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error when listening to the incoming channel @ " +
                        localAddress + ", will stop accepting incoming connections...", e);
            }
        }

        private void rejectIncomingConnection(SocketChannel channel) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Rejected incoming connection from {} due to exceeding of connections limit",
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

    private void acceptIncomingConnection(SocketChannel incomingChannel) {
        executor.execute(() -> {
            Peer peer = null;
            try {
                peer = peerRegistry.getPeerForAddress((InetSocketAddress) incomingChannel.getRemoteAddress());
            } catch (IOException e) {
                LOGGER.error("Failed to establish incoming connection", e);
                return;
            }

            try {
                Optional<PeerConnection> incomingConnection = connectionFactory.createIncomingConnection(peer, incomingChannel);
                if (incomingConnection.isPresent()) {
                    addOrGetExisting(incomingConnection.get());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to establish incoming connection from peer: " + peer, e);
            }
        });
    }

    private PeerConnection addOrGetExisting(PeerConnection newConnection) {
        Peer peer = newConnection.getRemotePeer();
        if (!addConnectionIfAbsent(newConnection)) {
            // check if it was already added simultaneously by another connection worker
            PeerConnection existingConnection = connections.get(peer)
                    .orElseThrow(() -> new RuntimeException("Failed to add new connection for peer: " + peer));
            if (existingConnection == null) {
                throw new RuntimeException("Failed to add new connection for peer: " + newConnection.getRemotePeer());
            }
            newConnection = existingConnection;
        }
        return newConnection;
    }

    private boolean addConnectionIfAbsent(PeerConnection newConnection) {
        boolean added = false;
        PeerConnection existingConnection = null;

        cleanerLock.lock();
        try {
            if (connections.count() >= config.getMaxPeerConnections()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Closing newly created connection with {} due to exceeding of connections limit",
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

        if (added) {
            eventSink.firePeerConnected(newConnection.getTorrentId(), newConnection.getRemotePeer());
        }
        return added;
    }

    private class Cleaner implements Runnable {

        private final Duration unreachablePeerBanDuration;

        Cleaner(Duration unreachablePeerBanDuration) {
            this.unreachablePeerBanDuration = unreachablePeerBanDuration;
        }

        @Override
        public void run() {
            if (connections.count() == 0) {
                return;
            }

            cleanerLock.lock();
            try {
                connections.visitConnections(connection -> {
                    Peer peer = connection.getRemotePeer();
                    if (connection.isClosed()) {
                        purgeConnectionWithPeer(peer);

                    } else if (System.currentTimeMillis() - connection.getLastActive()
                            >= peerConnectionInactivityThreshold.toMillis()) {

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Removing inactive peer connection: {}", peer);
                        }
                        purgeConnectionWithPeer(peer);
                    }
                    // can send keep-alives here based on lastActiveTime
                });

                unreachablePeers.forEach((peer, bannedAt) -> {
                    if (System.currentTimeMillis() - bannedAt >= unreachablePeerBanDuration.toMillis()) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Removing temporary ban for unreachable peer: {}", peer);
                        }
                        unreachablePeers.remove(peer);
                    }
                });

            } finally {
                cleanerLock.unlock();
            }
        }
    }

    private void purgeConnectionWithPeer(Peer peer) {
        PeerConnection purged = connections.remove(peer);
        if (purged != null) {
            if (!purged.isClosed()) {
                purged.closeQuietly();
            }
            eventSink.firePeerDisconnected(purged.getTorrentId(), peer);
        }
    }

    private void shutdown() {
        shutdownCleaner();
        connections.visitConnections(PeerConnection::closeQuietly);
    }

    private void shutdownCleaner() {
        cleaner.shutdown();
        try {
            cleaner.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for the cleaner's shutdown");
        }
        if (!cleaner.isShutdown()) {
            cleaner.shutdownNow();
        }
    }

    private static class Connections {
        private ConcurrentMap<Peer, PeerConnection> connections;
        private ConcurrentMap<TorrentId, Collection<PeerConnection>> connectionsByTorrent;

        Connections() {
            this.connections = new ConcurrentHashMap<>();
            this.connectionsByTorrent = new ConcurrentHashMap<>();
        }

        int count() {
            return connections.size();
        }

        synchronized PeerConnection remove(Peer peer) {
            PeerConnection removed = connections.remove(peer);
            if (removed != null) {
                connectionsByTorrent.values().forEach(connections -> {
                    connections.remove(removed);
                    if (connections.isEmpty()) {
                        connectionsByTorrent.remove(removed.getTorrentId());
                    }
                });
            }
            return removed;
        }

        synchronized PeerConnection putIfAbsent(Peer peer, PeerConnection connection) {
            PeerConnection existing = connections.putIfAbsent(peer, connection);
            TorrentId torrentId = connection.getTorrentId();
            if (existing == null && torrentId != null) {
                connectionsByTorrent.computeIfAbsent(torrentId, id -> ConcurrentHashMap.newKeySet()).add(connection);
            }
            return existing;
        }

        Optional<PeerConnection> get(Peer peer) {
            return Optional.ofNullable(connections.get(peer));
        }

        void visitConnections(Consumer<PeerConnection> visitor) {
            connections.values().forEach(visitor::accept);
        }

        void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor) {
            Collection<PeerConnection> connections = connectionsByTorrent.get(torrentId);
            if (connections != null) {
                connections.forEach(visitor::accept);
            }
        }
    }
}
