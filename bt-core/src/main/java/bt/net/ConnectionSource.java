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

import bt.CountingThreadFactory;
import bt.metainfo.TorrentId;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
    private final ExecutorService connectionExecutor;
    private final Config config;

    private final Map<ConnectionKey, CompletableFuture<ConnectionResult>> pendingConnections;
    // TODO: weak map
    private final ConcurrentMap<Peer, Long> unreachablePeers;

    @Inject
    public ConnectionSource(Set<PeerConnectionAcceptor> connectionAcceptors,
                            IPeerConnectionFactory connectionFactory,
                            IPeerConnectionPool connectionPool,
                            IRuntimeLifecycleBinder lifecycleBinder,
                            Config config) {

        this.connectionFactory = connectionFactory;
        this.connectionPool = connectionPool;
        this.config = config;

        this.connectionExecutor = Executors.newFixedThreadPool(
                config.getMaxPendingConnectionRequests(),
                CountingThreadFactory.daemonFactory("bt.net.pool.connection-worker"));
        lifecycleBinder.onShutdown("Shutdown connection workers", connectionExecutor::shutdownNow);

        this.pendingConnections = new ConcurrentHashMap<>();
        this.unreachablePeers = new ConcurrentHashMap<>();

        IncomingConnectionListener incomingListener =
                new IncomingConnectionListener(connectionAcceptors, connectionExecutor, connectionPool, config);
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
        ConnectionKey key = new ConnectionKey(peer, torrentId);

        CompletableFuture<ConnectionResult> connection = getExistingOrPendingConnection(key);
        if (connection != null) {
            if (connection.isDone() && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Returning existing connection for peer: {}. Torrent: {}", peer, torrentId);
            }
            return connection;
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

        if (connectionPool.size() >= config.getMaxPeerConnections()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Will not attempt to establish connection to peer: {}. " +
                        "Reason: connections limit exceeded. Torrent: {}", peer, torrentId);
            }
            return CompletableFuture.completedFuture(ConnectionResult.failure("Connections limit exceeded"));
        }

        synchronized (pendingConnections) {
            connection = getExistingOrPendingConnection(key);
            if (connection != null) {
                if (connection.isDone() && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Returning existing connection for peer: {}. Torrent: {}", peer, torrentId);
                }
                return connection;
            }

            connection = CompletableFuture.supplyAsync(() -> {
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
                    synchronized (pendingConnections) {
                        pendingConnections.remove(key);
                    }
                }
            }, connectionExecutor).whenComplete((acquiredConnection, throwable) -> {
                if (acquiredConnection == null || throwable != null) {
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

            pendingConnections.put(key, connection);
            return connection;
        }
    }

    private CompletableFuture<ConnectionResult> getExistingOrPendingConnection(ConnectionKey key) {
        PeerConnection existingConnection = connectionPool.getConnection(key);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(ConnectionResult.success(existingConnection));
        }

        CompletableFuture<ConnectionResult> pendingConnection = pendingConnections.get(key);
        if (pendingConnection != null) {
            return pendingConnection;
        }

        return null;
    }
}
