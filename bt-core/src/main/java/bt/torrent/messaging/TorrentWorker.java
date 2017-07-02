package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.runtime.Config;
import bt.data.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Manages peer workers.
 *
 * @since 1.0
 */
class TorrentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentWorker.class);

    private static final Duration UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL = Duration.ofSeconds(5);

    private TorrentId torrentId;
    private Bitfield bitfield;
    private Assignments assignments;
    private BitfieldBasedStatistics pieceStatistics;
    private IMessageDispatcher dispatcher;
    private Config config;

    private IPeerWorkerFactory peerWorkerFactory;
    private ConcurrentMap<Peer, PieceAnnouncingPeerWorker> peerMap;

    private long lastUpdatedAssignments;

    private Map<Peer, Long> timeoutedPeers;
    private Queue<Peer> disconnectedPeers;
    private Map<Peer, Message> interestUpdates;

    private final int MAX_CONCURRENT_ACTIVE_CONNECTIONS;

    public TorrentWorker(TorrentId torrentId,
                         IMessageDispatcher dispatcher,
                         Config config) {
        this.torrentId = torrentId;
        this.dispatcher = dispatcher;
        this.config = config;

        this.peerMap = new ConcurrentHashMap<>();
        this.timeoutedPeers = new ConcurrentHashMap<>();
        this.disconnectedPeers = new LinkedBlockingQueue<>();
        this.interestUpdates = new ConcurrentHashMap<>();

        this.MAX_CONCURRENT_ACTIVE_CONNECTIONS = config.getMaxConcurrentlyActivePeerConnectionsPerTorrent();
    }

    void setBitfield(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    void setAssignments(Assignments assignments) {
        this.assignments = assignments;
    }

    void setPieceStatistics(BitfieldBasedStatistics pieceStatistics) {
        this.pieceStatistics = pieceStatistics;
    }

    void setPeerWorkerFactory(IPeerWorkerFactory peerWorkerFactory) {
        this.peerWorkerFactory = peerWorkerFactory;
    }

    /**
     * Called when a peer joins the torrent processing session.
     *
     * @since 1.0
     */
    public void addPeer(Peer peer) {
        PieceAnnouncingPeerWorker worker = createPeerWorker(peer);
        PieceAnnouncingPeerWorker existing = peerMap.putIfAbsent(peer, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(peer, message -> consume(peer, message));
            dispatcher.addMessageSupplier(peer, () -> produce(peer));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Added connection for peer: " + peer);
            }
        }
    }

    private void consume(Peer peer, Message message) {
        PieceAnnouncingPeerWorker worker = getWorker(peer);
        worker.accept(message);
    }

    public Message produce(Peer peer) {
        PieceAnnouncingPeerWorker worker = getWorker(peer);

        Message message;
        if (bitfield != null && (bitfield.getPiecesRemaining() > 0 || assignments.count() > 0)) {
            inspectAssignment(peer);
            if (shouldUpdateAssignments()) {
                processDisconnectedPeers();
                processTimeoutedPeers();
                updateAssignments();
            }
            Message interestUpdate = interestUpdates.remove(peer);
            message = (interestUpdate == null) ? worker.get() : interestUpdate;
        } else {
            message = worker.get();
        }

        return message;
    }

    private PieceAnnouncingPeerWorker getWorker(Peer peer) {
        return Objects.requireNonNull(peerMap.get(peer), "Unknown peer: " + peer);
    }

    private void inspectAssignment(Peer peer) {
        ConnectionState connectionState = getWorker(peer).getConnectionState();
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
            if (mightCreateMoreAssignments()) {
                Optional<Assignment> newAssignment = assignments.assign(peer);
                if (newAssignment.isPresent()) {
                    newAssignment.get().start(connectionState);
                }
            }
        }
    }

    private boolean shouldUpdateAssignments() {
        return (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL.toMillis()
                    && mightUseMoreAssignees())
            || timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL.toMillis();
    }

    private boolean mightUseMoreAssignees() {
        return assignments.workersCount() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private boolean mightCreateMoreAssignments() {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private long timeSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdatedAssignments;
    }

    private void processDisconnectedPeers() {
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
            if (pieceStatistics != null) {
                pieceStatistics.removeBitfield(disconnectedPeer);
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

    private void updateAssignments() {
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
            ConnectionState connectionState = getWorker(peer).getConnectionState();
            if (connectionState.isInterested()) {
                interestUpdates.put(peer, NotInterested.instance());
                connectionState.setInterested(false);
            }
        });

        choking.forEach(peer -> {
            ConnectionState connectionState = getWorker(peer).getConnectionState();
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
}
