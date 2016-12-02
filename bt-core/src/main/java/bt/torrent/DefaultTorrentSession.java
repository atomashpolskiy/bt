package bt.torrent;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.torrent.messaging.IPeerWorkerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DefaultTorrentSession implements PeerActivityListener, TorrentSession {

    private IPeerConnectionPool connectionPool;
    private Torrent torrent;
    private TorrentWorker worker;
    private TorrentSessionState sessionState;

    private int maxPeerConnectionsPerTorrent;
    private final AtomicBoolean condition;

    public DefaultTorrentSession(IPeerConnectionPool connectionPool,
                                 IPieceManager pieceManager,
                                 IMessageDispatcher dispatcher,
                                 IPeerWorkerFactory peerWorkerFactory,
                                 Torrent torrent,
                                 int maxPeerConnectionsPerTorrent) {

        this.connectionPool = connectionPool;
        this.torrent = torrent;
        this.worker = new TorrentWorker(torrent.getTorrentId(), pieceManager, dispatcher, peerWorkerFactory);
        this.sessionState = new DefaultTorrentSessionState(pieceManager.getBitfield());
        this.maxPeerConnectionsPerTorrent = maxPeerConnectionsPerTorrent;
        this.condition = new AtomicBoolean(false);
    }

    @Override
    public void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        performSequentially(() -> {
            if (mightAddPeer(peer)) {
                connectionPool.requestConnection(torrent.getTorrentId(), peer);
            }
        });
    }

    @Override
    public void onPeerConnected(TorrentId torrentId, Peer peer) {
        performSequentially(() -> {
            if (mightAddPeer(peer) && torrent.getTorrentId().equals(torrentId)) {
                worker.addPeer(peer);
            }
        });
    }

    private void performSequentially(Runnable r) {
        while (!condition.compareAndSet(false, true))
            ;
        try {
            r.run();
        } finally {
            condition.set(false);
        }
    }

    private boolean mightAddPeer(Peer peer) {
        return worker.getPeers().size() < maxPeerConnectionsPerTorrent && !worker.getPeers().contains(peer);
    }

    @Override
    public void onPeerDisconnected(Peer peer) {
        worker.removePeer(peer);
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
