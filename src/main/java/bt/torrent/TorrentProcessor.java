package bt.torrent;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.net.HandshakeHandler;
import bt.net.IPeerConnection;
import bt.net.IPeerConnectionPool;
import bt.net.OutgoingHandshakeHandler;
import bt.net.Peer;
import bt.protocol.Have;
import bt.protocol.InvalidMessageException;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class TorrentProcessor implements Runnable, Consumer<IPeerConnection> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentProcessor.class);

    private IConfigurationService configurationService;
    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    private Torrent torrent;
    private ITorrentDescriptor torrentDescriptor;

    private Map<Peer, Long> peerBans;

    private Set<IPeerConnection> incomingConnections;
    private ConnectionRequestor connectionRequestor;

    private ConcurrentMap<IPeerConnection, ConnectionWorker> connectionWorkers;

    private List<BlockWrite> pendingBlockWrites;

    private ReentrantLock lock;
    private Condition timer;

    public TorrentProcessor(IPeerRegistry peerRegistry, IPeerConnectionPool connectionPool,
                            IConfigurationService configurationService, IPieceManager pieceManager,
                            IDataWorker dataWorker, Torrent torrent, ITorrentDescriptor torrentDescriptor) {

        this.configurationService = configurationService;
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.torrent = torrent;
        this.torrentDescriptor = torrentDescriptor;

        peerBans = new HashMap<>();

        incomingConnections = ConcurrentHashMap.newKeySet();

        HandshakeHandler outgoingHandler = new OutgoingHandshakeHandler(torrent, peerRegistry.getLocalPeer(),
                configurationService.getHandshakeTimeOut());
        connectionRequestor = new ConnectionRequestor(peerRegistry, connectionPool,
                outgoingHandler, configurationService, torrent);

        connectionWorkers = new ConcurrentHashMap<>();

        pendingBlockWrites = new ArrayList<>();

        lock = new ReentrantLock();
        timer = lock.newCondition();
    }

    @Override
    public synchronized void accept(IPeerConnection connection) {
        if (torrent.getInfoHash().equals(connection.getTag())) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Accepted incoming connection from peer: " + connection.getRemotePeer());
            }
            incomingConnections.add(connection);
        }
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
    }

    private void doProcess() {

        try {
            processBannedPeers();
            processIncomingConnections();
            requestConnectionsIfNeeded();
            processActiveConnections();
            processInactiveConnections();
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

    private void processIncomingConnections() {

        if (incomingConnections.isEmpty()) {
            return;
        }

        Iterator<IPeerConnection> iter = incomingConnections.iterator();
        while (iter.hasNext() && connectionWorkers.size() < configurationService.getMaxActiveConnectionsPerTorrent()) {

            IPeerConnection connection = iter.next();
            if (!peerBans.containsKey(connection.getRemotePeer())) {
                addConnection(connection);
            }
        }
    }

    private void requestConnectionsIfNeeded() {
        if (connectionWorkers.size() < configurationService.getMaxActiveConnectionsPerTorrent()) {
            connectionRequestor.requestConnections();
        }
    }

    private class ConnectionRequestor {

        private IPeerRegistry peerRegistry;
        private IPeerConnectionPool pool;
        private HandshakeHandler handshakeHandler;
        private IConfigurationService configurationService;
        private Torrent torrent;

        private Collection<Peer> peers;
        private long lastRequestedPeers;
        private Map<Peer, Long> lastRequestedConnections;

        ConnectionRequestor(IPeerRegistry peerRegistry, IPeerConnectionPool pool, HandshakeHandler handshakeHandler,
                            IConfigurationService configurationService, Torrent torrent) {
            this.peerRegistry = peerRegistry;
            this.pool = pool;
            this.handshakeHandler = handshakeHandler;
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
                        pool.requestConnection(peer, handshakeHandler)
                                .thenAccept(TorrentProcessor.this::addConnection);
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

    private void processActiveConnections() {

        if (connectionWorkers.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<IPeerConnection, ConnectionWorker>> workers = connectionWorkers.entrySet().iterator();
        while (workers.hasNext()) {

            Map.Entry<IPeerConnection, ConnectionWorker> entry = workers.next();
            ConnectionWorker worker = entry.getValue();
            try {
                worker.doWork();
            } catch (Throwable e) {
                IPeerConnection connection = entry.getKey();
                if (!connection.isClosed()) {
                    LOGGER.error("Closing peer connection (" + connection.getRemotePeer() + ") due to an error", e);
                    connection.closeQuietly();
                }
                LOGGER.info("Unexpected error in peer connection. Adding ban for peer: " + connection.getRemotePeer(), e);
                peerBans.put(connection.getRemotePeer(), System.currentTimeMillis());
                workers.remove();
            }
        }
    }

    private void processInactiveConnections() {

        if (connectionWorkers.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<IPeerConnection, ConnectionWorker>> workers = connectionWorkers.entrySet().iterator();
        while (workers.hasNext()) {

            IPeerConnection connection = workers.next().getKey();
            if (connection.isClosed()) {
                workers.remove();
            } else if (System.currentTimeMillis() - connection.getLastActive() >= configurationService.getMaxPeerInactivityInterval()) {
                if (!connection.isClosed()) {
                    LOGGER.info("Closing inactive connection; peer: " + connection.getRemotePeer());
                    connection.closeQuietly();
                }
                workers.remove();
            }
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
                        for (IPeerConnection connection : connectionWorkers.keySet()) {
                            if (!connection.isClosed()) {
                                connection.postMessage(have);
                            }
                        }
                    } catch (InvalidMessageException e) {
                        LOGGER.error("Unexpected error while announcing new completed pieces", e);
                    }
                }
                iter.remove();
            }
        }
    }

    private void addConnection(IPeerConnection connection) {

        ConnectionWorker existing = connectionWorkers.putIfAbsent(connection,
                new ConnectionWorker(connection, pieceManager,
                    request -> {
                        IConnectionState state = Objects.requireNonNull(connectionWorkers.get(connection)).getState();
                        if (state.isChoking()) {
                            // should not happen
                            throw new BtException("Received request to read block from choked worker");
                        }

                        if (!dataWorker.addBlockRequest(connection.getRemotePeer(),
                                    request.getPieceIndex(), request.getOffset(), request.getLength())) {
                            state.setChoking(true);
                        }
                    },
                    piece -> {
                        BlockWrite blockWrite = dataWorker.addBlock(connection.getRemotePeer(), piece.getPieceIndex(),
                                piece.getOffset(), piece.getBlock());
                        if (!blockWrite.isComplete() || blockWrite.isSuccess()) {
                            pendingBlockWrites.add(blockWrite);
                        }
                        return blockWrite;
                    },
                    () -> dataWorker.getCompletedBlockRequest(connection.getRemotePeer())));

        if (existing == null && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Added connection for peer: " + connection.getRemotePeer());
        }
    }
}
