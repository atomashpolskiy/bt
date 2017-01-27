package bt.peer;

import bt.metainfo.Torrent;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class TrackerPeerSourceFactory implements PeerSourceFactory {

    private ITrackerService trackerService;
    private Duration trackerQueryInterval;
    private Map<Torrent, TrackerPeerSource> peerSources;

    public TrackerPeerSourceFactory(ITrackerService trackerService, Duration trackerQueryInterval) {
        this.trackerService = trackerService;
        this.trackerQueryInterval = trackerQueryInterval;
        this.peerSources = new ConcurrentHashMap<>();
    }

    @Override
    public PeerSource getPeerSource(Torrent torrent) {
        TrackerPeerSource peerSource = peerSources.get(torrent);
        if (peerSource == null) {
            Optional<AnnounceKey> announceKey = torrent.getAnnounceKeyOptional();
            if (!announceKey.isPresent()) {
                throw new IllegalStateException("Torrent does not have an announce key");
            }
            peerSource = new TrackerPeerSource(trackerService.getTracker(announceKey.get()),
                    torrent, trackerQueryInterval);
            TrackerPeerSource existing = peerSources.putIfAbsent(torrent, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
