package bt.torrent.messaging;

import bt.event.EventSource;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import bt.torrent.stub.StubTorrent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DefaultTorrentSession implements TorrentSession {

    private final IPeerConnectionPool connectionPool;
    private final TorrentId torrentId;
    private final TorrentWorker worker;
    private final DefaultTorrentSessionState sessionState;

    private final TorrentRegistry torrentRegistry;

    private final int maxPeerConnectionsPerTorrent;
    private final AtomicBoolean condition;

    public DefaultTorrentSession(IPeerConnectionPool connectionPool,
                                 TorrentRegistry torrentRegistry,
                                 EventSource eventSource,
                                 TorrentWorker worker,
                                 TorrentId torrentId,
                                 TorrentDescriptor descriptor,
                                 int maxPeerConnectionsPerTorrent) {
        this.connectionPool = connectionPool;
        this.torrentRegistry = torrentRegistry;
        this.torrentId = torrentId;
        this.worker = worker;
        this.sessionState = new DefaultTorrentSessionState(descriptor);
        this.maxPeerConnectionsPerTorrent = maxPeerConnectionsPerTorrent;
        this.condition = new AtomicBoolean(false);

        eventSource.onPeerDiscovered(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDiscovered(e.getPeer());
            }
        });

        eventSource.onPeerConnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerConnected(e.getPeer());
            }
        });

        eventSource.onPeerDisconnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDisconnected(e.getPeer());
            }
        });
    }

    private void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        performSequentially(() -> {
            if (mightAddPeer(peer)) {
                connectionPool.requestConnection(torrentId, peer);
            }
        });
    }

    private void onPeerConnected(Peer peer) {
        performSequentially(() -> {
            if (mightAddPeer(peer)) {
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

    private void onPeerDisconnected(Peer peer) {
        worker.removePeer(peer);
    }

    @Override
    public Torrent getTorrent() {
        return torrentRegistry.getTorrent(torrentId).orElse(StubTorrent.instance());
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public TorrentSessionState getState() {
        return sessionState;
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

        private final TorrentDescriptor descriptor;

        DefaultTorrentSessionState(TorrentDescriptor descriptor) {
            this.recentAmountsForConnectedPeers = new HashMap<>();
            this.downloadedFromDisconnected = new AtomicLong();
            this.uploadedToDisconnected = new AtomicLong();
            this.descriptor = descriptor;
        }

        @Override
        public int getPiecesTotal() {
            if (descriptor.getDataDescriptor() != null) {
                return descriptor.getDataDescriptor().getBitfield().getPiecesTotal();
            } else {
                return 1;
            }
        }

        @Override
        public int getPiecesRemaining() {
            if (descriptor.getDataDescriptor() != null) {
                return descriptor.getDataDescriptor().getBitfield().getPiecesRemaining();
            } else {
                return 1;
            }
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
