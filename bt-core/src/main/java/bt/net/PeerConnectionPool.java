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
import bt.logging.MDCWrapper;
import bt.metainfo.TorrentId;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
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
    public PeerConnection getConnection(Peer peer) {
        return connections.get(peer).orElse(null);
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
        if (!addConnection(newConnection)) {
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
                        new MDCWrapper().putRemoteAddress(peer).run(() -> {
                            purgeConnectionWithPeer(peer);
                        });
                    } else if (System.currentTimeMillis() - connection.getLastActive()
                            >= peerConnectionInactivityThreshold.toMillis()) {
                        new MDCWrapper().putRemoteAddress(peer).run(() -> {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Removing inactive peer connection: {}", peer);
                            }
                            purgeConnectionWithPeer(peer);
                        });
                    }
                    // can send keep-alives here based on lastActiveTime
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
