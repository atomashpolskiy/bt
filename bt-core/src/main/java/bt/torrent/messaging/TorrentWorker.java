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

package bt.torrent.messaging;

import bt.data.Bitfield;
import bt.event.EventSource;
import bt.metainfo.TorrentId;
import bt.net.ConnectionKey;
import bt.net.IConnectionSource;
import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Manages peer workers.
 *
 * @since 1.0
 */
public class TorrentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentWorker.class);

    private static final Duration UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL = Duration.ofSeconds(5);

    private TorrentId torrentId;
    private IMessageDispatcher dispatcher;
    private Config config;

    private final IConnectionSource connectionSource;
    private IPeerWorkerFactory peerWorkerFactory;
    private ConcurrentMap<ConnectionKey, PieceAnnouncingPeerWorker> peerMap;
    private final int MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    private final int MAX_TOTAL_CONNECTIONS;
    private Map<ConnectionKey, Long> timeoutedPeers;
    private Queue<ConnectionKey> disconnectedPeers;
    private Map<ConnectionKey, Message> interestUpdates;
    private long lastUpdatedAssignments;

    private Supplier<Bitfield> bitfieldSupplier;
    private Supplier<Assignments> assignmentsSupplier;
    private Supplier<BitfieldBasedStatistics> statisticsSupplier;

    private ExecutorService assignmentsUpdateExecutorService;

    public TorrentWorker(TorrentId torrentId,
                         IMessageDispatcher dispatcher,
                         IConnectionSource connectionSource,
                         IPeerWorkerFactory peerWorkerFactory,
                         Supplier<Bitfield> bitfieldSupplier,
                         Supplier<Assignments> assignmentsSupplier,
                         Supplier<BitfieldBasedStatistics> statisticsSupplier,
                         EventSource eventSource,
                         Config config) {
        this.torrentId = torrentId;
        this.dispatcher = dispatcher;
        this.config = config;

        this.connectionSource = connectionSource;
        this.peerWorkerFactory = peerWorkerFactory;
        this.peerMap = new ConcurrentHashMap<>();
        this.MAX_CONCURRENT_ACTIVE_CONNECTIONS = config.getMaxConcurrentlyActivePeerConnectionsPerTorrent();
        this.MAX_TOTAL_CONNECTIONS = config.getMaxPeerConnectionsPerTorrent();
        this.timeoutedPeers = new ConcurrentHashMap<>();
        this.disconnectedPeers = new LinkedBlockingQueue<>();
        this.interestUpdates = new ConcurrentHashMap<>();

        this.bitfieldSupplier = bitfieldSupplier;
        this.assignmentsSupplier = assignmentsSupplier;
        this.statisticsSupplier = statisticsSupplier;

        eventSource.onPeerDiscovered(torrentId, e -> onPeerDiscovered(e.getPeer()));

        eventSource.onPeerConnected(torrentId, e -> onPeerConnected(e.getConnectionKey()));

        eventSource.onPeerDisconnected(torrentId, e -> onPeerDisconnected(e.getConnectionKey()));

        startAssignmentsUpdateThread(torrentId, eventSource, config);
    }

    private void startAssignmentsUpdateThread(TorrentId torrentId, EventSource eventSource, Config config) {
        String threadName = String.format("%d.bt.torrent.assignments.update.%s", config.getAcceptorPort(), torrentId);
        AtomicBoolean shutdown = new AtomicBoolean(false);
        assignmentsUpdateExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
        assignmentsUpdateExecutorService.submit(() -> {
            while (!shutdown.get()) {
                try {
                    updateAssignments();
                } catch (Throwable e) {
                    LOGGER.error(e.getMessage(), e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
        });

        eventSource.onTorrentStopped(torrentId, x -> {
            shutdown.set(true);
            assignmentsUpdateExecutorService.shutdown();
        });
    }

    private void updateAssignments() {
        Bitfield bitfield = getBitfield();
        Assignments assignments = getAssignments();

        if (bitfield != null && assignments != null && (assignments.count() > 0 || bitfield.getPiecesRemaining() > 0)) {
            if (shouldUpdateAssignments(assignments)) {
                processDisconnectedPeers(assignments, getStatistics());
                processTimeoutedPeers();
                updateAssignments(assignments);
            }
        }
    }

    private Bitfield getBitfield() {
        return bitfieldSupplier.get();
    }

    private Assignments getAssignments() {
        return assignmentsSupplier.get();
    }

    private BitfieldBasedStatistics getStatistics() {
        return statisticsSupplier.get();
    }

    /**
     * Called when a peer joins the torrent processing session.
     *
     * @since 1.0
     */
    private void addPeer(ConnectionKey connectionKey) {
        PieceAnnouncingPeerWorker worker = createPeerWorker(connectionKey);
        PieceAnnouncingPeerWorker existing = peerMap.putIfAbsent(connectionKey, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(connectionKey, message -> consume(connectionKey, message));
            dispatcher.addMessageSupplier(connectionKey, () -> produce(connectionKey));
        }
    }

    private void consume(ConnectionKey connectionKey, Message message) {
        getWorker(connectionKey).ifPresent(worker -> worker.accept(message));
    }

    private Message produce(ConnectionKey connectionKey) {
        Message message = null;

        Optional<PieceAnnouncingPeerWorker> workerOptional = getWorker(connectionKey);
        if (workerOptional.isPresent()) {
            PieceAnnouncingPeerWorker worker = workerOptional.get();
            Bitfield bitfield = getBitfield();
            Assignments assignments = getAssignments();

            if (bitfield != null && assignments != null && (bitfield.getPiecesRemaining() > 0 || assignments.count() > 0)) {
                inspectAssignment(connectionKey, worker, assignments);

                Message interestUpdate = interestUpdates.remove(connectionKey);
                message = (interestUpdate == null) ? worker.get() : interestUpdate;
            } else {
                message = worker.get();
            }
        }

        return message;
    }

    private Optional<PieceAnnouncingPeerWorker> getWorker(ConnectionKey connectionKey) {
        return Optional.ofNullable(peerMap.get(connectionKey));
    }

    private void inspectAssignment(ConnectionKey connectionKey, PeerWorker peerWorker, Assignments assignments) {
        ConnectionState connectionState = peerWorker.getConnectionState();
        Assignment assignment = assignments.get(connectionKey);
        if (assignment != null) {
            if (assignment.getStatus() == Assignment.Status.TIMEOUT) {
                timeoutedPeers.put(connectionKey, System.currentTimeMillis());
                assignments.remove(assignment);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Peer assignment removed due to TIMEOUT: {}", assignment);
                }
            } else if (connectionState.isPeerChoking()) {
                assignments.remove(assignment);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Peer assignment removed due to CHOKING: {}", assignment);
                }
            }
        } else if (!connectionState.isPeerChoking()) {
            if (mightCreateMoreAssignments(assignments)) {
                assignments.assign(connectionKey)
                        .ifPresent(newAssignment -> newAssignment.start(connectionState));
            }
        }
    }

    private boolean shouldUpdateAssignments(Assignments assignments) {
        return (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL.toMillis()
                && mightUseMoreAssignees(assignments))
                || timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL.toMillis();
    }

    private boolean mightUseMoreAssignees(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private boolean mightCreateMoreAssignments(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private long timeSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdatedAssignments;
    }

    private void processDisconnectedPeers(Assignments assignments, BitfieldBasedStatistics statistics) {
        ConnectionKey disconnectedPeer;
        while ((disconnectedPeer = disconnectedPeers.poll()) != null) {
            if (assignments != null) {
                Assignment assignment = assignments.get(disconnectedPeer);
                if (assignment != null) {
                    assignments.remove(assignment);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Peer assignment removed due to DISCONNECT: peer {}, assignment {}", disconnectedPeer, assignment);
                    }
                }
            }
            timeoutedPeers.remove(disconnectedPeer);
            if (statistics != null) {
                statistics.removeBitfield(disconnectedPeer);
            }
        }
    }

    private void processTimeoutedPeers() {
        timeoutedPeers.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() >=
                        config.getTimeoutedAssignmentPeerBanDuration().toMillis());
    }

    private void updateAssignments(Assignments assignments) {
        interestUpdates.clear();

        Set<ConnectionKey> ready = new HashSet<>();
        Set<ConnectionKey> choking = new HashSet<>();

        peerMap.forEach((peer, worker) -> {
            boolean timeouted = timeoutedPeers.containsKey(peer);
            boolean disconnected = disconnectedPeers.contains(peer);
            if (!timeouted && !disconnected) {
                if (worker.getConnectionState().isPeerChoking()) {
                    choking.add(peer);
                } else {
                    ready.add(peer);
                }
            }
        });

        Set<ConnectionKey> interesting = assignments.update(ready, choking);

        ready.stream().filter(peer -> !interesting.contains(peer)).forEach(peer -> {
            getWorker(peer).ifPresent(worker -> {
                ConnectionState connectionState = worker.getConnectionState();
                if (connectionState.isInterested()) {
                    interestUpdates.put(peer, NotInterested.instance());
                    connectionState.setInterested(false);
                }
            });
        });

        choking.forEach(peer -> {
            getWorker(peer).ifPresent(worker -> {
                ConnectionState connectionState = worker.getConnectionState();
                if (interesting.contains(peer)) {
                    if (!connectionState.isInterested()) {
                        interestUpdates.put(peer, Interested.instance());
                        connectionState.setInterested(true);
                    }
                } else if (connectionState.isInterested()) {
                    interestUpdates.put(peer, NotInterested.instance());
                    connectionState.setInterested(false);
                }
            });
        });

        lastUpdatedAssignments = System.currentTimeMillis();
    }

    private PieceAnnouncingPeerWorker createPeerWorker(ConnectionKey connectionKey) {
        return new PieceAnnouncingPeerWorker(peerWorkerFactory.createPeerWorker(connectionKey));
    }

    /**
     * Called when a peer leaves the torrent processing session.
     *
     * @since 1.0
     */
    public void removePeer(ConnectionKey connectionKey) {
        PeerWorker removed = peerMap.remove(connectionKey);
        if (removed != null) {
            disconnectedPeers.add(connectionKey);
        }
    }

    /**
     * Get all peers, that this torrent worker is currently working with.
     *
     * @since 1.9
     */
    public Set<ConnectionKey> getPeers() {
        return peerMap.keySet();
    }

    /**
     * Get the current state of a connection with a particular peer.
     *
     * @return Connection state or null, if the peer is not connected to this torrent worker
     * @since 1.0
     */
    public ConnectionState getConnectionState(ConnectionKey connectionKey) {
        PeerWorker worker = peerMap.get(connectionKey);
        return (worker == null) ? null : worker.getConnectionState();
    }

    private class PieceAnnouncingPeerWorker implements PeerWorker {

        private final PeerWorker delegate;
        private final Queue<Have> pieceAnnouncements;

        PieceAnnouncingPeerWorker(PeerWorker delegate) {
            this.delegate = delegate;
            this.pieceAnnouncements = new ConcurrentLinkedQueue<>();
        }

        @Override
        public ConnectionState getConnectionState() {
            return delegate.getConnectionState();
        }

        @Override
        public void accept(Message message) {
            delegate.accept(message);
        }

        @Override
        public Message get() {
            Message message = pieceAnnouncements.poll();

            if (message != null) {
                return message;
            }

            message = delegate.get();
            if (message != null && Have.class.equals(message.getClass())) {
                Have have = (Have) message;
                peerMap.values().forEach(worker -> {
                    if (this != worker) {
                        worker.getPieceAnnouncements().add(have);
                    }
                });
            }
            return message;
        }

        Queue<Have> getPieceAnnouncements() {
            return pieceAnnouncements;
        }
    }

    private synchronized void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        if (mightAddPeer()) {
            connectionSource.getConnectionAsync(peer, torrentId);
        }
    }

    private synchronized void onPeerConnected(ConnectionKey connectionKey) {
        if (mightAddPeer()) {
            addPeer(connectionKey);
        }
    }

    private boolean mightAddPeer() {
        return getPeers().size() < MAX_TOTAL_CONNECTIONS;
    }

    private synchronized void onPeerDisconnected(ConnectionKey connectionKey) {
        removePeer(connectionKey);
    }
}
