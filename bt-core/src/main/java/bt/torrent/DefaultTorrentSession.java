package bt.torrent;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.ConnectionHandler;
import bt.net.IConnectionHandlerFactory;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.protocol.Have;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.service.IConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefaultTorrentSession implements PeerActivityListener, TorrentSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTorrentSession.class);

    private IConfigurationService configurationService;
    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    private Torrent torrent;

    private IPeerConnectionPool connectionPool;
    private ConnectionHandler outgoingHandler;

    private ConcurrentMap<Peer, ConnectionWorker> connectionWorkers;

    private TorrentSessionState sessionState;

    public DefaultTorrentSession(IPeerConnectionPool connectionPool, IConfigurationService configurationService,
                                 IConnectionHandlerFactory connectionHandlerFactory,
                                 IPieceManager pieceManager, IDataWorker dataWorker, Torrent torrent) {

        this.connectionPool = connectionPool;
        this.configurationService = configurationService;
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.torrent = torrent;

        outgoingHandler = connectionHandlerFactory.getOutgoingHandler(torrent);
        connectionWorkers = new ConcurrentHashMap<>();

        sessionState = new TorrentSessionState() {

            @Override
            public int getPiecesTotal() {
                return pieceManager.getPiecesTotal();
            }

            @Override
            public int getPiecesRemaining() {
                return pieceManager.getPiecesRemaining();
            }

            @Override
            public long getDownloaded() {

                long downloaded = 0;
                for (ConnectionWorker worker : connectionWorkers.values()) {
                    downloaded += worker.getDownloaded();
                }
                return downloaded;
            }

            @Override
            public long getUploaded() {

                long uploaded = 0;
                for (ConnectionWorker worker : connectionWorkers.values()) {
                    uploaded += worker.getUploaded();
                }
                return uploaded;
            }

            @Override
            public Set<Peer> getConnectedPeers() {
                return Collections.unmodifiableSet(connectionWorkers.keySet());
            }
        };
    }

    public void onPeerDiscovered(Peer peer) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()
                || connectionWorkers.containsKey(peer)) {
            return;
        }
        connectionPool.requestConnection(peer, outgoingHandler);
    }

    @Override
    public void onPeerConnected(TorrentId torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                                Consumer<Supplier<Message>> messageSuppliers) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()) {
            return;
        }

        if (torrent.getTorrentId().equals(torrentId)) {

            ConnectionWorker worker = new ConnectionWorker(peer, pieceManager, dataWorker);
            ConnectionWorker existing = connectionWorkers.putIfAbsent(peer, worker);
            if (existing == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Added connection for peer: " + peer);
                }
                messageConsumers.accept(worker);
                messageSuppliers.accept(worker);
            }
        }
    }

    @Override
    public void onPeerDisconnected(Peer peer) {
        ConnectionWorker removed = connectionWorkers.remove(peer);
        if (removed != null) {
            removed.shutdown();
        }
    }

    public void onPieceVerified(Integer pieceIndex) {
        if (pieceManager.checkPieceVerified(pieceIndex)) {
            try {
                Have have = new Have(pieceIndex);
                for (ConnectionWorker worker : connectionWorkers.values()) {
                    worker.addMessage(have);
                }
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
}
