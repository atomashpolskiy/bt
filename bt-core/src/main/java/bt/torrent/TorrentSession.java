package bt.torrent;

import bt.metainfo.Torrent;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TorrentSession implements PeerActivityListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentSession.class);

    private IConfigurationService configurationService;
    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    private Torrent torrent;

    private IPeerConnectionPool connectionPool;
    private ConnectionHandler outgoingHandler;

    private ConcurrentMap<Peer, ConnectionWorker> connectionWorkers;

    public TorrentSession(IPeerConnectionPool connectionPool, IConfigurationService configurationService,
                          IConnectionHandlerFactory connectionHandlerFactory,
                          IPieceManager pieceManager, IDataWorker dataWorker, Torrent torrent) {

        this.connectionPool = connectionPool;
        this.configurationService = configurationService;
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.torrent = torrent;

        outgoingHandler = connectionHandlerFactory.getOutgoingHandler(torrent);
        connectionWorkers = new ConcurrentHashMap<>();
    }

    public void onPeerDiscovered(Peer peer) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()
                || connectionWorkers.containsKey(peer)) {
            return;
        }
        connectionPool.requestConnection(peer, outgoingHandler);
    }

    @Override
    public void onPeerConnected(Object torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                                Consumer<Supplier<Message>> messageSuppliers) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()) {
            return;
        }

        if (torrent.getInfoHash().equals(torrentId)) {

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

    public TorrentSessionState getState() {
        return () -> pieceManager.piecesLeft();
    }
}
