package bt.torrent.messaging.core;

import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.protocol.Have;
import bt.protocol.Message;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.PeerWorker;
import bt.torrent.messaging.IPeerWorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private TorrentId torrentId;
    private PieceManager pieceManager;
    private IMessageDispatcher dispatcher;

    private IPeerWorkerFactory peerWorkerFactory;
    private ConcurrentMap<Peer, PieceAnnouncingPeerWorker> peerMap;

    public TorrentWorker(TorrentId torrentId, PieceManager pieceManager, IMessageDispatcher dispatcher,
                         IPeerWorkerFactory peerWorkerFactory) {
        this.torrentId = torrentId;
        this.pieceManager = pieceManager;
        this.dispatcher = dispatcher;
        this.peerWorkerFactory = peerWorkerFactory;
        this.peerMap = new ConcurrentHashMap<>();
    }

    /**
     * Create a peer worker for a given peer.
     *
     * @since 1.0
     */
    public void addPeer(Peer peer) {
        PieceAnnouncingPeerWorker worker = createPeerWorker(peer);
        PieceAnnouncingPeerWorker existing = peerMap.putIfAbsent(peer, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(peer, worker::accept);
            dispatcher.addMessageSupplier(peer, worker::get);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Added connection for peer: " + peer);
            }
        }
    }

    private PieceAnnouncingPeerWorker createPeerWorker(Peer peer) {
        return new PieceAnnouncingPeerWorker(peerWorkerFactory.createPeerWorker(torrentId, peer));
    }

    /**
     * Create a peer worker for a given peer, if exists.
     *
     * @since 1.0
     */
    public void removePeer(Peer peer) {
        PeerWorker removed = peerMap.remove(peer);
        if (removed != null) {
            Optional<Integer> assignedPiece = pieceManager.getAssignedPiece(peer);
            if (assignedPiece.isPresent()) {
                pieceManager.unselectPieceForPeer(peer, assignedPiece.get());
            }
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
