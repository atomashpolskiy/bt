package bt.torrent.messaging;

import bt.data.Bitfield;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import bt.tracker.AnnounceKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
class DefaultTorrentSession implements TorrentSession, PeerActivityListener {

    private final IPeerConnectionPool connectionPool;
    private final TorrentId torrentId;
    private final TorrentWorker worker;
    private final DefaultTorrentSessionState sessionState;

    private volatile Optional<Torrent> torrent;

    private final int maxPeerConnectionsPerTorrent;
    private final AtomicBoolean condition;

    public DefaultTorrentSession(IPeerConnectionPool connectionPool,
                                 TorrentWorker worker,
                                 TorrentId torrentId,
                                 int maxPeerConnectionsPerTorrent) {
        this.connectionPool = connectionPool;
        this.torrentId = torrentId;
        this.worker = worker;
        this.sessionState = new DefaultTorrentSessionState();
        this.torrent = Optional.empty();
        this.maxPeerConnectionsPerTorrent = maxPeerConnectionsPerTorrent;
        this.condition = new AtomicBoolean(false);
    }

    void setTorrent(Torrent torrent) {
        this.torrent = Optional.of(torrent);
    }

    void setBitfield(Bitfield bitfield) {
        this.sessionState.setBitfield(bitfield);
    }

    @Override
    public void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        performSequentially(() -> {
            if (mightAddPeer(peer)) {
                connectionPool.requestConnection(torrentId, peer);
            }
        });
    }

    @Override
    public void onPeerConnected(TorrentId torrentId, Peer peer) {
        performSequentially(() -> {
            if (mightAddPeer(peer) && this.torrentId.equals(torrentId)) {
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
    public void onPeerDisconnected(TorrentId torrentId, Peer peer) {
        worker.removePeer(peer);
    }

    @Override
    public Torrent getTorrent() {
        return torrent.orElse(StubTorrent.instance());
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public TorrentSessionState getState() {
        return sessionState;
    }

    private static class StubTorrent implements Torrent {
        private static final StubTorrent instance = new StubTorrent();

        static StubTorrent instance() {
            return instance;
        }

        @Override
        public Optional<AnnounceKey> getAnnounceKey() {
            return Optional.empty();
        }

        @Override
        public TorrentId getTorrentId() {
            return TorrentId.fromBytes(new byte[20]);
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public long getChunkSize() {
            return 0;
        }

        @Override
        public Iterable<byte[]> getChunkHashes() {
            return Collections.emptyList();
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public List<TorrentFile> getFiles() {
            return Collections.emptyList();
        }

        @Override
        public boolean isPrivate() {
            return false;
        }
    }

    private class DefaultTorrentSessionState implements TorrentSessionState {

        private static final int DOWNLOADED_POSITION = 0;
        private static final int UPLOADED_POSITION = 1;

        /**
         * Recently calculated amounts of downloaded and uploaded data
         */
        private Map<Peer, Long[]> recentAmountsForConnectedPeers;

        /**
         * Historical data (amount of data downloaded from disconnected peers)
         */
        private volatile AtomicLong downloadedFromDisconnected;

        /**
         * Historical data (amount of data uploaded to disconnected peers)
         */
        private volatile AtomicLong uploadedToDisconnected;

        private volatile Optional<Bitfield> localBitfield;

        DefaultTorrentSessionState() {
            this.recentAmountsForConnectedPeers = new HashMap<>();
            this.downloadedFromDisconnected = new AtomicLong();
            this.uploadedToDisconnected = new AtomicLong();
        }

        void setBitfield(Bitfield bitfield) {
            this.localBitfield = Optional.of(bitfield);
        }

        @Override
        public int getPiecesTotal() {
            return localBitfield.isPresent() ? localBitfield.get().getPiecesTotal() : 1;
        }

        @Override
        public int getPiecesRemaining() {
            return localBitfield.isPresent() ? localBitfield.get().getPiecesRemaining() : 1;
        }

        @Override
        public synchronized long getDownloaded() {
            long downloaded = getCurrentAmounts().values().stream()
                    .collect(Collectors.summingLong(amounts -> amounts[DOWNLOADED_POSITION]));
            downloaded += downloadedFromDisconnected.get();
            return downloaded;
        }

        @Override
        public synchronized long getUploaded() {
            long uploaded = getCurrentAmounts().values().stream()
                    .collect(Collectors.summingLong(amounts -> amounts[UPLOADED_POSITION]));
            uploaded += uploadedToDisconnected.get();
            return uploaded;
        }

        private synchronized Map<Peer, Long[]> getCurrentAmounts() {
            Map<Peer, Long[]> connectedPeers = getAmountsForConnectedPeers();
            connectedPeers.forEach((peer, amounts) -> recentAmountsForConnectedPeers.put(peer, amounts));

            Set<Peer> disconnectedPeers = new HashSet<>();
            recentAmountsForConnectedPeers.forEach((peer, amounts) -> {
                if (!connectedPeers.containsKey(peer)) {
                    downloadedFromDisconnected.addAndGet(amounts[DOWNLOADED_POSITION]);
                    uploadedToDisconnected.addAndGet(amounts[UPLOADED_POSITION]);
                    disconnectedPeers.add(peer);
                }
            });
            disconnectedPeers.forEach(recentAmountsForConnectedPeers::remove);

            return recentAmountsForConnectedPeers;
        }

        private Map<Peer, Long[]> getAmountsForConnectedPeers() {
            return worker.getPeers().stream()
                    .collect(
                            HashMap::new,
                            (acc, peer) -> {
                                ConnectionState connectionState = worker.getConnectionState(peer);
                                acc.put(peer, new Long[] {connectionState.getDownloaded(), connectionState.getUploaded()});
                            },
                            HashMap::putAll);
        }

        @Override
        public Set<Peer> getConnectedPeers() {
            return Collections.unmodifiableSet(worker.getPeers());
        }
    }
}
