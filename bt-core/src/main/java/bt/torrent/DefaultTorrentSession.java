package bt.torrent;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.ConnectionHandler;
import bt.net.IConnectionHandlerFactory;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.protocol.Have;
import bt.protocol.InvalidMessageException;
import bt.service.IConfigurationService;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.TorrentWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

public class DefaultTorrentSession implements PeerActivityListener, TorrentSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTorrentSession.class);

    private IConfigurationService configurationService;
    private IPieceManager pieceManager;

    private Torrent torrent;

    private IPeerConnectionPool connectionPool;
    private ConnectionHandler outgoingHandler;

    private TorrentSessionState sessionState;
    private TorrentWorker worker;

    public DefaultTorrentSession(IPeerConnectionPool connectionPool, IConfigurationService configurationService,
                                 IConnectionHandlerFactory connectionHandlerFactory,
                                 IPieceManager pieceManager, IMessageDispatcher dispatcher,
                                 IPeerWorkerFactory peerWorkerFactory, Torrent torrent) {

        this.connectionPool = connectionPool;
        this.configurationService = configurationService;
        this.pieceManager = pieceManager;

        this.torrent = torrent;

        this.outgoingHandler = connectionHandlerFactory.getOutgoingHandler(torrent);
        this.sessionState = new DefaultTorrentSessionState(pieceManager.getBitfield());
        this.worker = new TorrentWorker(pieceManager, dispatcher, peerWorkerFactory);
    }

    @Override
    public void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        if (worker.getPeers().size() >= configurationService.getMaxActiveConnectionsPerTorrent()
                || worker.getPeers().contains(peer)) {
            return;
        }
        connectionPool.requestConnection(peer, outgoingHandler);
    }

    @Override
    public void onPeerConnected(TorrentId torrentId, Peer peer) {
        if (worker.getPeers().size() >= configurationService.getMaxActiveConnectionsPerTorrent()) {
            return;
        }
        if (torrent.getTorrentId().equals(torrentId)) {
            worker.addPeer(peer);
        }
    }

    @Override
    public void onPeerDisconnected(Peer peer) {
        worker.removePeer(peer);
    }

    public void onPieceVerified(Integer pieceIndex) {
        if (pieceManager.checkPieceVerified(pieceIndex)) {
            try {
                Have have = new Have(pieceIndex);
                worker.broadcast(have);
            } catch (InvalidMessageException e) {
                LOGGER.error("Unexpected error while announcing new completed piece", e);
            }
        }
    }

    @Override
    public Torrent getTorrent() {
        return torrent;
    }

    @Override
    public TorrentSessionState getState() {
        return sessionState;
    }

    private class DefaultTorrentSessionState implements TorrentSessionState {

        private Bitfield localBitfield;

        DefaultTorrentSessionState(Bitfield localBitfield) {
            this.localBitfield = localBitfield;
        }

        @Override
        public int getPiecesTotal() {
            return localBitfield.getPiecesTotal();
        }

        @Override
        public int getPiecesRemaining() {
            return localBitfield.getPiecesRemaining();
        }

        @Override
        public long getDownloaded() {

            long downloaded = 0;
            for (Peer peer : worker.getPeers()) {
                downloaded += worker.getConnectionState(peer).getDownloaded();
            }
            return downloaded;
        }

        @Override
        public long getUploaded() {

            long uploaded = 0;
            for (Peer peer : worker.getPeers()) {
                uploaded += worker.getConnectionState(peer).getUploaded();
            }
            return uploaded;
        }

        @Override
        public Set<Peer> getConnectedPeers() {
            return Collections.unmodifiableSet(worker.getPeers());
        }
    }
}
