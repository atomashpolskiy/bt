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
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
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
    private ConcurrentMap<Peer, PieceAnnouncingPeerWorker> peerMap;
    private final int MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    private final int MAX_TOTAL_CONNECTIONS;
    private Map<Peer, Long> timeoutedPeers;
    private Queue<Peer> disconnectedPeers;
    private Map<Peer, Message> interestUpdates;
    private long lastUpdatedAssignments;

    private Supplier<Bitfield> bitfieldSupplier;
    private Supplier<Assignments> assignmentsSupplier;
    private Supplier<BitfieldBasedStatistics> statisticsSupplier;

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

        eventSource.onPeerDiscovered(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDiscovered(e.getPeer());
            }
        });

        eventSource.onPeerConnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerConnected(e.getPeer());
            }
        });

        eventSource.onPeerDisconnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDisconnected(e.getPeer());
            }
        });
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
    private void addPeer(Peer peer) {
        PieceAnnouncingPeerWorker worker = createPeerWorker(peer);
        PieceAnnouncingPeerWorker existing = peerMap.putIfAbsent(peer, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(torrentId, peer, message -> consume(peer, message));
            dispatcher.addMessageSupplier(torrentId, peer, () -> produce(peer));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Added connection for peer: " + peer);
            }
        }
    }

    private void consume(Peer peer, Message message) {
        getWorker(peer).ifPresent(worker -> worker.accept(message));
    }

    private Message produce(Peer peer) {
        Message message = null;

        Optional<PieceAnnouncingPeerWorker> workerOptional = getWorker(peer);
        if (workerOptional.isPresent()) {
            PieceAnnouncingPeerWorker worker = workerOptional.get();
            Bitfield bitfield = getBitfield();
            Assignments assignments = getAssignments();

            if (bitfield != null && assignments != null && (bitfield.getPiecesRemaining() > 0 || assignments.count() > 0)) {
                inspectAssignment(peer, worker, assignments);
                if (shouldUpdateAssignments(assignments)) {
                    processDisconnectedPeers(assignments, getStatistics());
                    processTimeoutedPeers();
                    updateAssignments(assignments);
                }
                Message interestUpdate = interestUpdates.remove(peer);
                message = (interestUpdate == null) ? worker.get() : interestUpdate;
            } else {
                message = worker.get();
            }
        }

        return message;
    }

    private Optional<PieceAnnouncingPeerWorker> getWorker(Peer peer) {
        return Optional.ofNullable(peerMap.get(peer));
    }

    private void inspectAssignment(Peer peer, PeerWorker peerWorker, Assignments assignments) {
        ConnectionState connectionState = peerWorker.getConnectionState();
        Assignment assignment = assignments.get(peer);
        boolean shouldAssign;
        if (assignment != null) {
            switch (assignment.getStatus()) {
                case ACTIVE: {
                    shouldAssign = false;
                    break;
                }
                case DONE: {
                    // assign next piece
                    assignments.remove(assignment);
                    shouldAssign = true;
                    break;
                }
                case TIMEOUT: {
                    timeoutedPeers.put(peer, System.currentTimeMillis());
                    assignments.remove(assignment);
                    shouldAssign = false;
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Peer assignment removed due to TIMEOUT: {}", assignment);
                    }
                    break;
                }
                default: {
                    throw new IllegalStateException("Unexpected status: " + assignment.getStatus().name());
                }
            }
        } else {
            shouldAssign = true;
        }

        if (connectionState.isPeerChoking()) {
            if (assignment != null) {
                assignments.remove(assignment);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Peer assignment removed due to CHOKING: {}", assignment);
                }
            }
        } else if (shouldAssign) {
            if (mightCreateMoreAssignments(assignments)) {
                Optional<Assignment> newAssignment = assignments.assign(peer);
                if (newAssignment.isPresent()) {
                    newAssignment.get().start(connectionState);
                }
            }
        }
    }

    private boolean shouldUpdateAssignments(Assignments assignments) {
        return (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL.toMillis()
                    && mightUseMoreAssignees(assignments))
            || timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL.toMillis();
    }

    private boolean mightUseMoreAssignees(Assignments assignments) {
        return assignments.workersCount() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private boolean mightCreateMoreAssignments(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private long timeSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdatedAssignments;
    }

    private void processDisconnectedPeers(Assignments assignments, BitfieldBasedStatistics statistics) {
        Peer disconnectedPeer;
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
        Iterator<Map.Entry<Peer, Long>> timeoutedPeersIter = timeoutedPeers.entrySet().iterator();
        while (timeoutedPeersIter.hasNext()) {
            Map.Entry<Peer, Long> entry = timeoutedPeersIter.next();
            if (System.currentTimeMillis() - entry.getValue() >= config.getTimeoutedAssignmentPeerBanDuration().toMillis()) {
                timeoutedPeersIter.remove();
            }
        }
    }

    private void updateAssignments(Assignments assignments) {
        interestUpdates.clear();

        Set<Peer> ready = new HashSet<>();
        Set<Peer> choking = new HashSet<>();

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

        Set<Peer> interesting = assignments.update(ready, choking);

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

    private PieceAnnouncingPeerWorker createPeerWorker(Peer peer) {
        return new PieceAnnouncingPeerWorker(peerWorkerFactory.createPeerWorker(torrentId, peer));
    }

    /**
     * Called when a peer leaves the torrent processing session.
     *
     * @since 1.0
     */
    public void removePeer(Peer peer) {
        PeerWorker removed = peerMap.remove(peer);
        if (removed != null) {
            disconnectedPeers.add(peer);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Removed connection for peer: " + peer);
            }
        }
    }

    /**
     * Get all peers, that this torrent worker is currently working with.
     *
     * @since 1.0
     */
    public Set<Peer> getPeers() {
        return peerMap.keySet();
    }

    /**
     * Get the current state of a connection with a particular peer.
     *
     * @return Connection state or null, if the peer is not connected to this torrent worker
     * @since 1.0
     */
    public ConnectionState getConnectionState(Peer peer) {
        PeerWorker worker = peerMap.get(peer);
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
            Message message = pieceAnnouncements.poll();;
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
        if (mightAddPeer(peer)) {
            connectionSource.getConnectionAsync(peer, torrentId);
        }
    }

    private synchronized void onPeerConnected(Peer peer) {
        if (mightAddPeer(peer)) {
            addPeer(peer);
        }
    }

    private boolean mightAddPeer(Peer peer) {
        return getPeers().size() < MAX_TOTAL_CONNECTIONS && !getPeers().contains(peer);
    }

    private synchronized void onPeerDisconnected(Peer peer) {
        removePeer(peer);
    }
}
