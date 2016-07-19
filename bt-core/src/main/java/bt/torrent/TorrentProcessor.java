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

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TorrentProcessor implements Runnable, PeerActivityListener, Consumer<Peer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentProcessor.class);

    private IConfigurationService configurationService;
    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    private Torrent torrent;
    private ITorrentDescriptor torrentDescriptor;

    private IPeerConnectionPool connectionPool;
    private ConnectionHandler outgoingHandler;

    private ConcurrentMap<Peer, ConnectionWorker> connectionWorkers;

    private Set<BlockWrite> pendingBlockWrites;

    private ReentrantLock lock;
    private Condition timer;

    public TorrentProcessor(IPeerConnectionPool connectionPool, IConfigurationService configurationService,
                            IConnectionHandlerFactory connectionHandlerFactory,
                            IPieceManager pieceManager, IDataWorker dataWorker, Torrent torrent,
                            ITorrentDescriptor torrentDescriptor) {

        this.connectionPool = connectionPool;
        this.configurationService = configurationService;
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.torrent = torrent;
        this.torrentDescriptor = torrentDescriptor;

        outgoingHandler = connectionHandlerFactory.getOutgoingHandler(torrent);

        connectionWorkers = new ConcurrentHashMap<>();

        pendingBlockWrites = ConcurrentHashMap.newKeySet();

        lock = new ReentrantLock();
        timer = lock.newCondition();
    }

    @Override
    public void run() {
        while (torrentDescriptor.isActive()) {

            doProcess();

            lock.lock();
            try {
                timer.await(1, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while awaiting timer", e);
            } finally {
                lock.unlock();
            }
        }
        // TODO: remove listener from connection pool
    }

    private void doProcess() {

        try {
            processPendingBlockWrites();
        } catch (Exception e) {
            LOGGER.error("Unexpected error in torrent processor {torrent: " + torrent + "}", e);
        }
    }

    @Override
    public void accept(Peer peer) {
        onPeerDiscovered(peer);
    }

    public void onPeerDiscovered(Peer peer) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()) {
            return;
        }

        if (!connectionWorkers.containsKey(peer)) {
            connectionPool.requestConnection(peer, outgoingHandler);
        }
    }

    @Override
    public void onPeerConnected(Object torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                                Consumer<Supplier<Message>> messageSuppliers) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()) {
            return;
        }

        if (torrent.getInfoHash().equals(torrentId)) {
            ConnectionWorker worker = new ConnectionWorker(peer, pieceManager, dataWorker,
                blockWrite -> pendingBlockWrites.add(blockWrite));

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

    private void processPendingBlockWrites() {

        if (pendingBlockWrites.isEmpty()) {
            return;
        }

        Iterator<BlockWrite> iter = pendingBlockWrites.iterator();
        while (iter.hasNext()) {

            BlockWrite pendingBlockWrite = iter.next();
            if (pendingBlockWrite.isComplete()) {

                int pieceIndex = pendingBlockWrite.getPieceIndex();
                if (pendingBlockWrite.isSuccess() && pieceManager.checkPieceVerified(pieceIndex)) {
                    try {
                        Have have = new Have(pieceIndex);
                        for (ConnectionWorker worker : connectionWorkers.values()) {
                            worker.addMessage(have);
                        }
                    } catch (InvalidMessageException e) {
                        LOGGER.error("Unexpected error while announcing new completed pieces", e);
                    }
                }
                iter.remove();
            }
        }
    }
}
