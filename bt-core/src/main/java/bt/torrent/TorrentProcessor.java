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
import bt.service.IPeerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TorrentProcessor implements Runnable, PeerActivityListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentProcessor.class);

    private IConfigurationService configurationService;
    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    private Torrent torrent;
    private ITorrentDescriptor torrentDescriptor;

    private Map<Peer, Long> peerBans;

    private ConnectionRequestor connectionRequestor;

    private ConcurrentMap<Peer, ConnectionWorker> connectionWorkers;

    private Set<BlockWrite> pendingBlockWrites;

    private ReentrantLock lock;
    private Condition timer;

    public TorrentProcessor(IPeerRegistry peerRegistry, IPeerConnectionPool connectionPool,
                            IConfigurationService configurationService, IConnectionHandlerFactory connectionHandlerFactory,
                            IPieceManager pieceManager, IDataWorker dataWorker, Torrent torrent,
                            ITorrentDescriptor torrentDescriptor) {

        this.configurationService = configurationService;
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.torrent = torrent;
        this.torrentDescriptor = torrentDescriptor;

        peerBans = new HashMap<>();

        ConnectionHandler outgoingHandler = connectionHandlerFactory.getOutgoingHandler(torrent);
        connectionRequestor = new ConnectionRequestor(peerRegistry, connectionPool,
                outgoingHandler, configurationService, torrent);

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
            processBannedPeers();
            requestConnectionsIfNeeded();
            processPendingBlockWrites();

        } catch (Exception e) {
            LOGGER.error("Unexpected error in torrent processor {torrent: " + torrent + "}", e);
        }
    }

    private void processBannedPeers() {

        if (peerBans.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Peer, Long>> iter = peerBans.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Peer, Long> peerBan = iter.next();
            if (System.currentTimeMillis() - peerBan.getValue() >= configurationService.getPeerBanTime()) {
                LOGGER.info("Removing ban for peer: " + peerBan.getKey());
                iter.remove();
            }
        }
    }

    private void requestConnectionsIfNeeded() {
        if (connectionWorkers.size() < configurationService.getMaxActiveConnectionsPerTorrent()) {
            connectionRequestor.requestConnections();
        }
    }

    @Override
    public void onPeerConnected(Object torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                                Consumer<Supplier<Message>> messageSuppliers) {

        if (connectionWorkers.size() >= configurationService.getMaxActiveConnectionsPerTorrent()
                || peerBans.containsKey(peer)) {
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

    private class ConnectionRequestor {

        private IPeerRegistry peerRegistry;
        private IPeerConnectionPool pool;
        private ConnectionHandler connectionHandler;
        private IConfigurationService configurationService;
        private Torrent torrent;

        private Collection<Peer> peers;
        private long lastRequestedPeers;
        private Map<Peer, Long> lastRequestedConnections;

        ConnectionRequestor(IPeerRegistry peerRegistry, IPeerConnectionPool pool, ConnectionHandler connectionHandler,
                            IConfigurationService configurationService, Torrent torrent) {
            this.peerRegistry = peerRegistry;
            this.pool = pool;
            this.connectionHandler = connectionHandler;
            this.configurationService = configurationService;
            this.torrent = torrent;

            lastRequestedConnections = new HashMap<>();
        }

        void requestConnections() {
            refreshPeers();

            if (peers.isEmpty()) {
                return;
            }

            for (Peer peer : peers) {
                if (mightConnect(peer)) {
                    long currentTimeMillis = System.currentTimeMillis();
                    Long lastRequestedConnection = lastRequestedConnections.get(peer);
                    if (lastRequestedConnection == null || currentTimeMillis - lastRequestedConnection >= 60000) {
                        lastRequestedConnections.put(peer, currentTimeMillis);
                        // TODO: remove this from here (should move to MessageDispatcher probably)
                        pool.requestConnection(peer, connectionHandler);
                    }
                }
            }
        }

        private void refreshPeers() {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastRequestedPeers >= configurationService.getPeerRefreshThreshold()) {
                peers = peerRegistry.getPeersForTorrent(torrent);
                lastRequestedPeers = currentTimeMillis;
            }
        }

        private boolean mightConnect(Peer peer) {
            return !peerBans.containsKey(peer);
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
