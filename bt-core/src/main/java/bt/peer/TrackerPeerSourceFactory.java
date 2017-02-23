package bt.peer;

import bt.metainfo.Torrent;
import bt.service.IRuntimeLifecycleBinder;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class TrackerPeerSourceFactory implements PeerSourceFactory {

    private ITrackerService trackerService;
    private Duration trackerQueryInterval;
    private Map<Torrent, TrackerPeerSource> peerSources;

    private ExecutorService executor;

    public TrackerPeerSourceFactory(ITrackerService trackerService,
                                    IRuntimeLifecycleBinder lifecycleBinder,
                                    Duration trackerQueryInterval) {
        this.trackerService = trackerService;
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
    public PeerSource getPeerSource(Torrent torrent) {
        TrackerPeerSource peerSource = peerSources.get(torrent);
        if (peerSource == null) {
            Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
            if (!announceKey.isPresent()) {
                throw new IllegalStateException("Torrent does not have an announce key");
            }
            peerSource = new TrackerPeerSource(executor, trackerService.getTracker(announceKey.get()),
                    torrent, trackerQueryInterval);
            TrackerPeerSource existing = peerSources.putIfAbsent(torrent, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
