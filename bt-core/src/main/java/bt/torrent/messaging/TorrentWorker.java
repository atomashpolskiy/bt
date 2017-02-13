package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.torrent.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.selector.PieceSelector;
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

/**
 * Manages peer workers.
 *
 * @since 1.0
 */
class TorrentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentWorker.class);

    private static final Duration UPDATE_ASSIGNMENTS_INTERVAL = Duration.ofSeconds(1);
    private static final int MAX_CONCURRENT_ACTIVE_CONNECTIONS = 20;

    private static class PeerStatus {

        private static final Duration MAX_PIECE_RECEIVING_TIME = Duration.ofMinutes(1);
        private static final Duration MAX_BLOCK_RECEIVING_INTERVAL = Duration.ofSeconds(5);

        enum Status { CHOKING, READY, WAITING, ACTIVE, TIMEOUT };

        private Peer peer;
        private ConnectionState connectionState;
        private Assignments assignments;
        private Status status;
        private long timeAssignedPiece;

        PeerStatus(Peer peer, ConnectionState connectionState, Assignments assignments) {
            this.peer = peer;
            this.connectionState = connectionState;
            this.assignments = assignments;
        }

        Status getStatus() {
            status = determineStatus();
            return status;
        }

        private Status determineStatus() {
            if (!assignments.hasAssignedPiece(peer)) {
                return connectionState.isPeerChoking() ? Status.CHOKING : Status.READY;
            }

            long timeSinceAssignedPiece = System.currentTimeMillis() - timeAssignedPiece;
            if (timeSinceAssignedPiece > MAX_PIECE_RECEIVING_TIME.toMillis()) {
                return Status.TIMEOUT;
            }

            long timeSinceLastReceivedBlock = System.currentTimeMillis() - connectionState.getLastReceivedBlock();
            if (timeSinceLastReceivedBlock > MAX_BLOCK_RECEIVING_INTERVAL.toMillis()) {
                return Status.WAITING;
            }

            return Status.ACTIVE;
        }

        void markAssignedPiece() {
            this.timeAssignedPiece = System.currentTimeMillis();
        }
    }

    private TorrentId torrentId;
    private Bitfield bitfield;
    private Assignments assignments;
    private PieceSelector pieceSelector;
    private BitfieldBasedStatistics pieceStatistics;
    private IMessageDispatcher dispatcher;

    private IPeerWorkerFactory peerWorkerFactory;
    private ConcurrentMap<Peer, PieceAnnouncingPeerWorker> peerMap;

    private long lastUpdated;

    private Map<Peer, PeerStatus> interestingPeers;
    private Set<Peer> timeoutedPeers;
    private Map<Peer, Message> interestUpdates;

    public TorrentWorker(TorrentId torrentId,
                         Bitfield bitfield,
                         Assignments assignments,
                         PieceSelector pieceSelector,
                         BitfieldBasedStatistics pieceStatistics,
                         IMessageDispatcher dispatcher,
                         IPeerWorkerFactory peerWorkerFactory) {
        this.torrentId = torrentId;
        this.bitfield = bitfield;
        this.assignments = assignments;
        this.pieceSelector = pieceSelector;
        this.pieceStatistics = pieceStatistics;
        this.dispatcher = dispatcher;
        this.peerWorkerFactory = peerWorkerFactory;

        this.peerMap = new ConcurrentHashMap<>();
        this.interestingPeers = new ConcurrentHashMap<>();
        this.timeoutedPeers = ConcurrentHashMap.newKeySet();
        this.interestUpdates = new ConcurrentHashMap<>();
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
        if (!assignments.hasAssignedPiece(peer) && mightUpdateAssignments()) {
            updateAssignments();
            lastUpdated = System.currentTimeMillis();
        }
        Message interestUpdate = interestUpdates.remove(peer);
        return (interestUpdate == null) ? worker.get() : interestUpdate;
    }

    private PieceAnnouncingPeerWorker getWorker(Peer peer) {
        return Objects.requireNonNull(peerMap.get(peer), "Unknown peer: " + peer);
    }

    private boolean mightUpdateAssignments() {
        return (bitfield.getPiecesRemaining() > 0)
                && (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_INTERVAL.toMillis())
                && (assignments.getAssignees().size() < MAX_CONCURRENT_ACTIVE_CONNECTIONS);
    }

    private long timeSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdated;
    }

    private void updateAssignments() {
        Set<Peer> readyPeers = new HashSet<>();

        Iterator<Map.Entry<Peer, PeerStatus>> iter = interestingPeers.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Peer, PeerStatus> e = iter.next();
            Peer peer = e.getKey();
            PeerStatus status = e.getValue();

            switch (status.getStatus()) {
                case TIMEOUT: {
                    iter.remove();
                    timeoutedPeers.add(peer);
                    assignments.removeAssignment(peer);
                    continue;
                }
                case READY: {
                    readyPeers.add(peer);
                    continue;
                }
                case CHOKING: {
                    assignments.removeAssignment(peer);
                    ConnectionState connectionState = getWorker(peer).getConnectionState();
                    connectionState.onUnassign();
                    continue;
                }
                case WAITING: {
                    continue;
                }
                case ACTIVE: {
                    continue;
                }
                default: {
                    throw new IllegalStateException("Unexpected status: " + status.getStatus().name());
                }
            }
        }

        Set<Peer> inactivePeers = new HashSet<>(peerMap.keySet());
        inactivePeers.removeAll(interestingPeers.keySet());
        inactivePeers.removeAll(timeoutedPeers);

        Iterator<Integer> suggestedPieces = pieceSelector.getNextPieces(pieceStatistics).iterator();
        while (suggestedPieces.hasNext()
                && (readyPeers.size() > 0 || inactivePeers.size() > 0)
                && assignments.getAssignees().size() < MAX_CONCURRENT_ACTIVE_CONNECTIONS) {

            Integer suggestedPiece = suggestedPieces.next();

            for (Peer readyPeer : readyPeers) {
                if (hasPiece(readyPeer, suggestedPiece)) {
                    assignments.assignPiece(readyPeer, suggestedPiece);
                    interestingPeers.get(readyPeer).markAssignedPiece();
                    readyPeers.remove(readyPeer);
                    break;
                }
            }

            for (Peer inactivePeer : inactivePeers) {
                if (hasPiece(inactivePeer, suggestedPiece)) {
                    ConnectionState connectionState = getWorker(inactivePeer).getConnectionState();
                    if (!connectionState.isInterested()) {
                        interestUpdates.put(inactivePeer, Interested.instance());
                        connectionState.setInterested(true);
                    }
                    interestingPeers.put(inactivePeer, new PeerStatus(inactivePeer, connectionState, assignments));
                    inactivePeers.remove(inactivePeer);
                    break;
                }
            }
        }

        for (Peer inactivePeer : inactivePeers) {
            ConnectionState connectionState = getWorker(inactivePeer).getConnectionState();
            if (connectionState.isInterested()) {
                interestUpdates.put(inactivePeer, NotInterested.instance());
                connectionState.setInterested(false);
            }
        }
    }

    private boolean hasPiece(Peer peer, Integer pieceIndex) {
        Optional<Bitfield> bitfield = pieceStatistics.getPeerBitfield(peer);
        return bitfield.isPresent() && bitfield.get().isVerified(pieceIndex);
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
            pieceStatistics.removeBitfield(peer);
            assignments.removeAssignment(peer);
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
