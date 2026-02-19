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

package bt.torrent.messaging;

import bt.data.LocalBitfield;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final ConcurrentMap<ConnectionKey, PieceAnnouncingPeerWorker> peerMap;
    // This is an atomic measure of the length of peerMap, to avoid synchronization which may result
    // in deadlock. It is eventually consistent.
    private final AtomicInteger peerCount;

    private final int MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    private final int MAX_TOTAL_CONNECTIONS;
    private Map<ConnectionKey, Long> timeoutedPeers;
    private Queue<ConnectionKey> disconnectedPeers;
    private Map<ConnectionKey, Message> interestUpdates;
    private long lastUpdatedAssignments;
    private long lastNumInterestingPeers;

    private Supplier<LocalBitfield> bitfieldSupplier;
    private Supplier<Assignments> assignmentsSupplier;
    private Supplier<BitfieldBasedStatistics> statisticsSupplier;

    public TorrentWorker(TorrentId torrentId,
                         IMessageDispatcher dispatcher,
                         IConnectionSource connectionSource,
                         IPeerWorkerFactory peerWorkerFactory,
                         Supplier<LocalBitfield> bitfieldSupplier,
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
        this.peerCount = new AtomicInteger(0);
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
    }

    private LocalBitfield getBitfield() {
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
        if (tryAddPeerWithLimits()) {
            // If worker was null, our peerCount could diverge from the size peerMap. In practice,
            // worker is never null, and ConcurrentHashMap cannot hold null values, but we add requireNonNull
            // to make this more clear.
            PieceAnnouncingPeerWorker worker = Objects.requireNonNull(createPeerWorker(connectionKey));
            PieceAnnouncingPeerWorker existing = peerMap.putIfAbsent(connectionKey, worker);
            if (existing == null) {
                dispatcher.setConnectionMessageConsumerAndSupplier(connectionKey,
                        message -> consume(connectionKey, message), () -> produce(connectionKey));
            } else {
                // The peer was already present, so don't increment the count.
                peerCount.decrementAndGet();
            }
        }
    }

    private boolean tryAddPeerWithLimits() {
        // Increase the peer count if we're under the limit. Otherwise, keep it the same.
        int prevPeerCount = peerCount
                .getAndUpdate(currPeerCount -> underPeerLimit(currPeerCount) ? currPeerCount + 1 : currPeerCount);

        // if the count before the CAS was within the limit, we incremented the peer count and can add this peer.
        // if it was not under the limit, we're full, didn't change the count and cannot add this peer.
        return underPeerLimit(prevPeerCount);
    }

    private boolean underPeerLimit(int currPeerCount) {
        return currPeerCount < MAX_TOTAL_CONNECTIONS;
    }

    private void consume(ConnectionKey connectionKey, Message message) {
        PieceAnnouncingPeerWorker worker = getWorker(connectionKey);
        if (worker != null) {
            worker.accept(message);
        }
    }

    private Message produce(ConnectionKey connectionKey) {
        Message message = null;

        PieceAnnouncingPeerWorker worker = getWorker(connectionKey);
        if (worker != null) {
            LocalBitfield bitfield = getBitfield();
            Assignments assignments = getAssignments();

            if (bitfield != null && assignments != null && (bitfield.getPiecesRemaining() > 0 || assignments.count() > 0)) {
                inspectAssignment(connectionKey, worker, assignments);
                if (shouldUpdateAssignments(assignments)) {
                    processDisconnectedPeers(assignments, getStatistics());
                    processTimeoutedPeers();
                    updateAssignments(assignments);
                }
                Message interestUpdate = interestUpdates.remove(connectionKey);
                message = (interestUpdate == null) ? worker.get() : interestUpdate;
            } else {
                message = worker.get();
            }
        }

        return message;
    }

    private PieceAnnouncingPeerWorker getWorker(ConnectionKey connectionKey) {
        return peerMap.get(connectionKey);
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
        return lastNumInterestingPeers == 0 || // Immediately update assigment if we previously had no interesting peers
                (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL.toMillis()
                        && mightUseMoreAssignees(assignments))
                || timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL.toMillis();
    }

    private boolean mightUseMoreAssignees(Assignments assignments) {
        return lessAssignmentsThanConnections(assignments);
    }

    private boolean lessAssignmentsThanConnections(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private boolean mightCreateMoreAssignments(Assignments assignments) {
        return lessAssignmentsThanConnections(assignments);
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
                        LOGGER.trace("Peer assignment removed due to DISCONNECT: peer {}, assignment {}",
                                disconnectedPeer, assignment);
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
            PieceAnnouncingPeerWorker worker = getWorker(peer);
            if (worker != null) {
                ConnectionState connectionState = worker.getConnectionState();
                if (connectionState.isInterested()) {
                    interestUpdates.put(peer, NotInterested.instance());
                    connectionState.setInterested(false);
                }
            }
        });

        choking.forEach(peer -> {
            PieceAnnouncingPeerWorker worker = getWorker(peer);
            if (worker != null) {
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
            }
        });

        lastUpdatedAssignments = System.currentTimeMillis();
        lastNumInterestingPeers = interesting.size();
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
            peerCount.decrementAndGet();
        }
    }

    /**
     * Get all peers, that this torrent worker is currently working with.
     *
     * @since 1.9
     */
    public Set<ConnectionKey> getPeers() {
        return Collections.unmodifiableSet(peerMap.keySet());
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
            ;
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

    private void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        if (underPeerLimit(peerCount.get())) {
            connectionSource.getConnectionAsync(peer, torrentId);
        }
    }

    private void onPeerConnected(ConnectionKey connectionKey) {
        addPeer(connectionKey);
    }

    private void onPeerDisconnected(ConnectionKey connectionKey) {
        removePeer(connectionKey);
    }
}
