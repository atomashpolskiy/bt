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
import bt.event.EventSink;
import bt.metainfo.TorrentId;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerConnectionPool implements IPeerConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnectionPool.class);

    private Config config;
    private EventSink eventSink;
    private ScheduledExecutorService cleaner;
    private Connections connections;
    private ReentrantLock cleanerLock;
    private Duration peerConnectionInactivityThreshold;

    @Inject
    public PeerConnectionPool(
            EventSink eventSink,
            IRuntimeLifecycleBinder lifecycleBinder,
            Config config) {

        this.config = config;
        this.eventSink = eventSink;
        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();
        this.connections = new Connections();
        this.cleanerLock = new ReentrantLock();

        this.cleaner = Executors.newScheduledThreadPool(1, r -> new Thread(r, "bt.net.pool.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of stale peer connections",
                () -> cleaner.scheduleAtFixedRate(new Cleaner(), 1, 1, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newFixedThreadPool(
                config.getMaxPendingConnectionRequests(),
                CountingThreadFactory.daemonFactory("bt.net.pool.connection-worker"));

        lifecycleBinder.onShutdown("Shutdown outgoing connection request processor", executor::shutdownNow);
        lifecycleBinder.onShutdown("Shutdown connection pool", this::shutdown);
    }

    @Override
    public PeerConnection getConnection(Peer peer, TorrentId torrentId) {
        return connections.get(peer, torrentId).orElse(null);
    }

    @Override
    public PeerConnection getConnection(ConnectionKey key) {
        return connections.get(key).orElse(null);
    }

    @Override
    public void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor) {
        connections.visitConnections(torrentId, visitor);
    }

    @Override
    public int size() {
        return connections.count();
    }

    @Override
    public PeerConnection addConnectionIfAbsent(PeerConnection newConnection) {
        Peer peer = newConnection.getRemotePeer();
        TorrentId torrentId = newConnection.getTorrentId();

        if (!addConnection(newConnection)) {
            // check if it was already added simultaneously by another connection worker
            PeerConnection existingConnection = connections.get(peer, torrentId)
                    .orElseThrow(() -> new RuntimeException("Failed to add new connection for peer: " + peer));
            if (existingConnection == null) {
                throw new RuntimeException("Failed to add new connection for peer: " + newConnection.getRemotePeer());
            }
            newConnection = existingConnection;
        }
        return newConnection;
    }

    private boolean addConnection(PeerConnection newConnection) {
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
                existingConnection = connections.putIfAbsent(newConnection);
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
        @Override
        public void run() {
            if (connections.count() == 0) {
                return;
            }

            cleanerLock.lock();
            try {
                connections.visitConnections(connection -> {
                    if (connection.isClosed()) {
                        purgeConnection(connection);

                    } else if (System.currentTimeMillis() - connection.getLastActive()
                            >= peerConnectionInactivityThreshold.toMillis()) {

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Removing inactive peer connection: {}", connection.getRemotePeer());
                        }
                        purgeConnection(connection);
                    }
                    // can send keep-alives here based on lastActiveTime
                });

            } finally {
                cleanerLock.unlock();
            }
        }
    }

    private void purgeConnection(PeerConnection connection) {
        connections.remove(connection);
        connection.closeQuietly();
        eventSink.firePeerDisconnected(connection.getTorrentId(), connection.getRemotePeer());
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
}

class Connections {
    private ConcurrentMap<ConnectionKey, PeerConnection> connections;
    private ConcurrentMap<TorrentId, Collection<PeerConnection>> connectionsByTorrent;

    Connections() {
        this.connections = new ConcurrentHashMap<>();
        this.connectionsByTorrent = new ConcurrentHashMap<>();
    }

    int count() {
        return connections.size();
    }

    synchronized boolean remove(PeerConnection connection) {
        Objects.requireNonNull(connection);

        Peer peer = connection.getRemotePeer();
        TorrentId torrentId = connection.getTorrentId();
        ConnectionKey key = new ConnectionKey(peer, torrentId);

        PeerConnection removed = connections.remove(key);
        boolean success = (removed == connection);
        if (success) {
            Collection<PeerConnection> torrentConnections = connectionsByTorrent.get(torrentId);
            torrentConnections.remove(removed);
            if (torrentConnections.isEmpty()) {
                connectionsByTorrent.remove(torrentId);
            }
        }
        return success;
    }

    synchronized PeerConnection putIfAbsent(PeerConnection connection) {
        Objects.requireNonNull(connection);

        Peer peer = connection.getRemotePeer();
        TorrentId torrentId = connection.getTorrentId();
        ConnectionKey key = new ConnectionKey(peer, torrentId);

        PeerConnection existing = connections.putIfAbsent(key, connection);
        if (existing == null) {
            connectionsByTorrent.computeIfAbsent(torrentId, id -> ConcurrentHashMap.newKeySet())
                    .add(connection);
        }
        return existing;
    }

    Optional<PeerConnection> get(Peer peer, TorrentId torrentId) {
        return get(new ConnectionKey(peer, torrentId));
    }

    Optional<PeerConnection> get(ConnectionKey key) {
        return Optional.ofNullable(connections.get(key));
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
