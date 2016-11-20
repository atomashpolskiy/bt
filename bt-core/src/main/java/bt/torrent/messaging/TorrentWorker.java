package bt.torrent.messaging;

import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.torrent.IPieceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages peer workers.
 *
 * @since 1.0
 */
public class TorrentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentWorker.class);

    private IPieceManager pieceManager;
    private IMessageDispatcher dispatcher;

    private IPeerWorkerFactory peerWorkerFactory;
    private ConcurrentMap<Peer, IPeerWorker> peerMap;

    public TorrentWorker(IPieceManager pieceManager, IMessageDispatcher dispatcher,
                         IPeerWorkerFactory peerWorkerFactory) {
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
        IPeerWorker worker = peerWorkerFactory.createPeerWorker(peer);
        IPeerWorker existing = peerMap.putIfAbsent(peer, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(peer, worker::accept);
            dispatcher.addMessageSupplier(peer, worker::get);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Added connection for peer: " + peer);
            }
        }
    }

    /**
     * Create a peer worker for a given peer, if exists.
     *
     * @since 1.0
     */
    public void removePeer(Peer peer) {
        IPeerWorker removed = peerMap.remove(peer);
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
        IPeerWorker worker = peerMap.get(peer);
        return (worker == null) ? null : worker.getConnectionState();
    }
}
