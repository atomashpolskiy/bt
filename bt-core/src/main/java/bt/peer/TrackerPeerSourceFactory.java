package bt.peer;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class TrackerPeerSourceFactory implements PeerSourceFactory {

    private ITrackerService trackerService;
    private TorrentRegistry torrentRegistry;
    private Duration trackerQueryInterval;
    private Map<TorrentId, TrackerPeerSource> peerSources;

    private ExecutorService executor;

    public TrackerPeerSourceFactory(ITrackerService trackerService,
                                    TorrentRegistry torrentRegistry,
                                    IRuntimeLifecycleBinder lifecycleBinder,
                                    Duration trackerQueryInterval) {
        this.trackerService = trackerService;
        this.torrentRegistry = torrentRegistry;
        this.trackerQueryInterval = trackerQueryInterval;
        this.peerSources = new ConcurrentHashMap<>();

        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "bt.peer.tracker-peer-source-" + i.incrementAndGet());
            }
        });
        lifecycleBinder.onShutdown("Shutdown tracker peer sources", executor::shutdownNow);
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        TrackerPeerSource peerSource = peerSources.get(torrentId);
        if (peerSource == null) {
            Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);
            if (!torrentOptional.isPresent()) {
                // return a mock peer source instead of failing, because torrent might be being fetched at the time
                return noopSource;
            }

            Torrent torrent = torrentOptional.get();
            Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
            if (!announceKey.isPresent()) {
                throw new IllegalStateException("Torrent does not have an announce key");
            }
            peerSource = new TrackerPeerSource(executor, trackerService.getTracker(announceKey.get()),
                    torrentId, trackerQueryInterval);
            TrackerPeerSource existing = peerSources.putIfAbsent(torrentId, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }

    private static final PeerSource noopSource = new PeerSource() {
        @Override
        public boolean update() {
            return false;
        }

        @Override
        public Collection<Peer> getPeers() {
            return Collections.emptyList();
        }
    };
}
