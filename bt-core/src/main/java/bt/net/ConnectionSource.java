/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.CountingThreadFactory;
import bt.metainfo.TorrentId;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.PeerTimeoutRegistry;     // ✅ Added import
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionSource implements IConnectionSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionSource.class);

    private final IPeerConnectionFactory connectionFactory;
    private final IPeerConnectionPool connectionPool;
    private final ExecutorService outgoingConnectionExecutor;
    private final Config config;
    private final Object lock = new Object();

    private final ConcurrentMap<ConnectionKey, CompletableFuture<ConnectionResult>> pendingConnections;
    private final ConcurrentMap<Peer, Long> unreachablePeers;

    private final PeerTimeoutRegistry peerTimeoutRegistry;   // ✅ Added field

    @Inject
    public ConnectionSource(Set<PeerConnectionAcceptor> connectionAcceptors,
                            IPeerConnectionFactory connectionFactory,
                            IPeerConnectionPool connectionPool,
                            IRuntimeLifecycleBinder lifecycleBinder,
                            Config config) {

        this.connectionFactory = connectionFactory;
        this.connectionPool = connectionPool;
        this.config = config;

        String outgoingThreadName = String.format("%d.bt.net.pool.outgoing-connection-worker", config.getAcceptorPort());
        this.outgoingConnectionExecutor = Executors.newFixedThreadPool(
                config.getMaxPendingConnectionRequests(),
                CountingThreadFactory.daemonFactory(outgoingThreadName));
        lifecycleBinder.onShutdown("Shutdown outgoing connection workers", outgoingConnectionExecutor::shutdownNow);

        this.pendingConnections = new ConcurrentHashMap<>();
        this.unreachablePeers = new ConcurrentHashMap<>();

        // ✅ Initialize PeerTimeoutRegistry (1-minute bans)
        this.peerTimeoutRegistry = new PeerTimeoutRegistry(1, java.util.concurrent.TimeUnit.MINUTES);

        String incomingThreadName = String.format("%d.bt.net.pool.incoming-connection-worker", config.getAcceptorPort());
        ExecutorService incomingConnectionExecutor = Executors.newFixedThreadPool(
                config.getMaxPendingConnectionRequests(),
                CountingThreadFactory.daemonFactory(incomingThreadName));
        lifecycleBinder.onShutdown("Shutdown incoming connection workers", incomingConnectionExecutor::shutdownNow);

        IncomingConnectionListener incomingListener =
                new IncomingConnectionListener(connectionAcceptors, incomingConnectionExecutor, connectionPool, config);
        lifecycleBinder.onStartup("Initialize incoming connection acceptors", incomingListener::startup);
        lifecycleBinder.onShutdown("Shutdown incoming connection acceptors", incomingListener::shutdown);
    }

    @Override
    public ConnectionResult getConnection(Peer peer, TorrentId torrentId) {
        try {
            return getConnectionAsync(peer, torrentId).get();
        } catch (InterruptedException e) {
            return ConnectionResult.failure("Interrupted while waiting for connection", e);
        } catch (ExecutionException e) {
            return ConnectionResult.failure("Failed to establish connection due to error", e);
        }
    }

    @Override
    public CompletableFuture<ConnectionResult> getConnectionAsync(Peer peer, TorrentId torrentId) {
        ConnectionKey key = new ConnectionKey(peer, peer.getPort(), torrentId);

        // ✅ Check for temporarily banned peers
        String peerId = peer.toString();
        if (!peerTimeoutRegistry.isAllowed(peerId)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping banned peer: {}", peerId);
            }
            return CompletableFuture.completedFuture(ConnectionResult.failure("Peer is temporarily banned"));
        }

        CompletableFuture<ConnectionResult> result = validateNewConnPossible(peer, torrentId, key);
        if (result != null) {
            return result;
        }

        Long bannedAt = unreachablePeers.get(peer);
        if (bannedAt != null) {
            if (System.currentTimeMillis() - bannedAt >= config.getUnreachablePeerBanDuration().toMillis()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Removing temporary ban for unreachable peer: {}", peer);
                }
                unreachablePeers.remove(peer);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Will not attempt to establish connection to peer: {}. " +
                            "Reason: peer is unreachable. Torrent: {}", peer, torrentId);
                }
                return CompletableFuture.completedFuture(ConnectionResult.failure("Peer is unreachable"));
            }
        }

        synchronized (lock) {
            result = validateNewConnPossible(peer, torrentId, key);
            if (result != null) {
                return result;
            } else {
                CompletableFuture<Void> addedToPendingConnections = new CompletableFuture<>();
                try {
                    result = createPendingConnFuture(peer, torrentId, key, addedToPendingConnections);
                    pendingConnections.put(key, result);
                    return result;
                } finally {
                    addedToPendingConnections.complete(null);
                }
            }
        }
    }

    private CompletableFuture<ConnectionResult> createPendingConnFuture(Peer peer, TorrentId torrentId,
                                                                        ConnectionKey key,
                                                                        CompletableFuture<Void> addedToPendingConnections) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConnectionResult connectionResult =
                        connectionFactory.createOutgoingConnection(peer, torrentId);
                if (connectionResult.isSuccess()) {
                    PeerConnection established = connectionResult.getConnection();
                    PeerConnection added = connectionPool.addConnectionIfAbsent(established);
                    if (added != established) {
                        established.closeQuietly();
                    }
                    return ConnectionResult.success(added);
                } else {
                    return connectionResult;
                }
            } finally {
                addedToPendingConnections.join();
                synchronized (pendingConnections) {
                    pendingConnections.remove(key);
                }
            }
        }, outgoingConnectionExecutor).whenComplete((acquiredConnection, throwable) -> {
            if (acquiredConnection == null || throwable != null) {
                // ✅ Mark peer as timed out in registry
                peerTimeoutRegistry.markTimedOut(peer.toString());

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Peer is unreachable: {}. Will prevent further attempts to establish connection.", peer);
                }
                unreachablePeers.putIfAbsent(peer, System.currentTimeMillis());
            }
            if (throwable != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Failed to establish outgoing connection to peer: " + peer, throwable);
                }
            }
        });
    }

    private CompletableFuture<ConnectionResult> validateNewConnPossible(Peer peer, TorrentId torrentId,
                                                                        ConnectionKey key) {
        CompletableFuture<ConnectionResult> connection = getExistingOrPendingConnection(key);
        if (connection != null) {
            if (connection.isDone() && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Returning existing connection for peer: {}. Torrent: {}", peer, torrentId);
            }
            return connection;
        }

        if (checkPeerConnectionsLimit(peer, torrentId)) {
            return CompletableFuture.completedFuture(ConnectionResult.failure("Connections limit exceeded"));
        }
        return null;
    }

    private boolean checkPeerConnectionsLimit(Peer peer, TorrentId torrentId) {
        if (connectionPool.size() >= config.getMaxPeerConnections()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Will not attempt to establish connection to peer: {}. " +
                        "Reason: connections limit exceeded. Torrent: {}", peer, torrentId);
            }
            return true;
        }
        return false;
    }

    private CompletableFuture<ConnectionResult> getExistingOrPendingConnection(ConnectionKey key) {
        synchronized (pendingConnections) {
            CompletableFuture<ConnectionResult> pendingConnection = pendingConnections.get(key);
            if (pendingConnection != null) {
                return pendingConnection;
            }
        }

        PeerConnection existingConnection = connectionPool.getConnection(key);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(ConnectionResult.success(existingConnection));
        }

        return null;
    }
}
