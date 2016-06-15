package bt.torrent;

import bt.metainfo.Torrent;
import bt.net.HandshakeHandler;
import bt.net.IPeerConnection;
import bt.net.OutgoingHandshakeHandler;
import bt.net.Peer;
import bt.net.PeerConnectionPool;
import bt.protocol.Have;
import bt.protocol.InvalidMessageException;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class TorrentProcessor implements Runnable, Consumer<IPeerConnection> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentProcessor.class);

    private IPeerRegistry peerRegistry;
    private PeerConnectionPool connectionPool;
    private IConfigurationService configurationService;
    private IPieceManager pieceManager;
    private DataWorker dataWorker;

    private Torrent torrent;

    private HandshakeHandler handshakeHandler;

    private Map<Peer, Long> peerBans;

    private Set<IPeerConnection> incomingConnections;
    private Map<Peer, IPeerConnection> connections;
    private Set<Peer> requestedPoolConnections;
    private ConnectionRequestor connectionRequestor;

    private Map<IPeerConnection, ConnectionWorker> connectionWorkers;

    private List<BlockWrite> pendingBlockWrites;

    private boolean complete;

    private ReentrantLock lock;
    private Condition timer;

    public TorrentProcessor(IPeerRegistry peerRegistry, PeerConnectionPool connectionPool,
                            IConfigurationService configurationService, IPieceManager pieceManager,
                            DataWorker dataWorker, Torrent torrent) {

        this.peerRegistry = peerRegistry;
        this.connectionPool = connectionPool;
        this.configurationService = configurationService;
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.torrent = torrent;

        handshakeHandler = new OutgoingHandshakeHandler(torrent, peerRegistry.getLocalPeer(),
                configurationService.getHandshakeTimeOut());

        peerBans = new HashMap<>();

        incomingConnections = new HashSet<>();
        connections = new HashMap<>();
        requestedPoolConnections = new HashSet<>();
        connectionRequestor = new ConnectionRequestor(peerRegistry, connectionPool,
                handshakeHandler, configurationService, torrent);

        connectionWorkers = new HashMap<>();

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

        long start = System.currentTimeMillis();
        long step = 0;
        lock.lock();
        try {
            while (!complete) {
                if (++step % 1_000L == 0) {
                    LOGGER.info("Working... pieces left: " + pieceManager.piecesLeft() + " {connections: " + connections.size() +
                            ", workers: " + connectionWorkers.size() + "}");
                }
                doProcess();

                try {
                    timer.await(1, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while awaiting timer", e);
                }
            }
        } finally {
            lock.unlock();
        }
        Duration completedIn = Duration.ofMillis(System.currentTimeMillis() - start);
        LOGGER.info("Done in " + completedIn.getSeconds() + " sec");
    }

    private void doProcess() {

        try {
            processBannedPeers();
            processRequestedConnections();
            processIncomingConnections();
            requestConnectionsIfNeeded();
            processActiveConnections();
            processInactiveConnections();
            processPendingBlockWrites();
            checkTorrentIsComplete();

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

    private void processRequestedConnections() {

        if (requestedPoolConnections.isEmpty()) {
            return;
        }

        Iterator<Peer> iter = requestedPoolConnections.iterator();
        while (iter.hasNext()) {
            Peer peer = iter.next();
            IPeerConnection connection = connectionPool.getConnection(peer);
            if (connection != null) {
                addConnection(connection);
                iter.remove();
            }
        }
    }

    private void processIncomingConnections() {

        if (incomingConnections.isEmpty()) {
            return;
        }

        Iterator<IPeerConnection> iter = incomingConnections.iterator();
        while (iter.hasNext() && connections.size() < configurationService.getMaxActiveConnectionsPerTorrent()) {

            IPeerConnection connection = iter.next();
            if (!peerBans.containsKey(connection.getRemotePeer())) {
                addConnection(connection);
            }
        }
    }

    private void requestConnectionsIfNeeded() {
        if (connections.size() < configurationService.getMaxActiveConnectionsPerTorrent()) {
            requestedPoolConnections.addAll(connectionRequestor.requestConnections());
        }
    }

    private class ConnectionRequestor {

        private IPeerRegistry peerRegistry;
        private PeerConnectionPool pool;
        private HandshakeHandler handshakeHandler;
        private IConfigurationService configurationService;
        private Torrent torrent;

        private Collection<Peer> peers;
        private long lastRequestedPeers;
        private Map<Peer, Long> lastRequestedConnections;

        ConnectionRequestor(IPeerRegistry peerRegistry, PeerConnectionPool pool, HandshakeHandler handshakeHandler,
                            IConfigurationService configurationService, Torrent torrent) {
            this.peerRegistry = peerRegistry;
            this.pool = pool;
            this.handshakeHandler = handshakeHandler;
            this.configurationService = configurationService;
            this.torrent = torrent;

            lastRequestedConnections = new HashMap<>();
        }

        List<Peer> requestConnections() {
            refreshPeers();

            if (peers.isEmpty()) {
                return Collections.emptyList();
            }

            List<Peer> requestedConnections = new ArrayList<>();
            for (Peer peer : peers) {
                if (mightConnect(peer)) {
                    long currentTimeMillis = System.currentTimeMillis();
                    Long lastRequestedConnection = lastRequestedConnections.get(peer);
                    if (lastRequestedConnection == null || currentTimeMillis - lastRequestedConnection >= 60000) {
                        lastRequestedConnections.put(peer, currentTimeMillis);
                        pool.requestConnection(peer, handshakeHandler);
                        requestedConnections.add(peer);
                    }
                }
            }
            return requestedConnections;
        }

        private void refreshPeers() {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastRequestedPeers >= configurationService.getPeerRefreshThreshold()) {
                peers = peerRegistry.getPeersForTorrent(torrent);
                lastRequestedPeers = currentTimeMillis;
            }
        }

        private boolean mightConnect(Peer peer) {
            return !connections.containsKey(peer) && !requestedPoolConnections.contains(peer)
                    && !peerBans.containsKey(peer);
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
                Collection<BlockWrite> blockWrites = worker.doWork();
                pendingBlockWrites.addAll(blockWrites);
            } catch (Throwable e) {
                IPeerConnection connection = entry.getKey();
                if (!connection.isClosed()) {
                    LOGGER.error("Closing peer connection (" + connection.getRemotePeer() + ") due to an error", e);
                    connection.closeQuietly();
                    peerBans.put(connection.getRemotePeer(), System.currentTimeMillis());
                }
                LOGGER.info("Unexpected error in peer connection. Adding ban for peer: " + connection.getRemotePeer(), e);
                workers.remove();
                removeConnection(connection);
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
                removeConnection(connection);
            } else if (System.currentTimeMillis() - connection.getLastActive() >= configurationService.getMaxPeerInactivityInterval()) {
                if (!connection.isClosed()) {
                    LOGGER.info("Closing inactive connection; peer: " + connection.getRemotePeer());
                    connection.closeQuietly();
                }
                workers.remove();
                removeConnection(connection);
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
                        for (IPeerConnection connection : connections.values()) {
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

    private void checkTorrentIsComplete() {
        if (pieceManager.piecesLeft() == 0) {
            complete = true;
            LOGGER.info("Torrent is complete: " + torrent);
        }
    }

    private void addConnection(IPeerConnection connection) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Adding connection for peer: " + connection.getRemotePeer());
        }
        connections.put(connection.getRemotePeer(), connection);
        connectionWorkers.put(connection, new ConnectionWorker(pieceManager, dataWorker, connection));
    }

    private void removeConnection(IPeerConnection connection) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removing connection for peer: " + connection.getRemotePeer());
        }
        connections.remove(connection.getRemotePeer());
    }
}
