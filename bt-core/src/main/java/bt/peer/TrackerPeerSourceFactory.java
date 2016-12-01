package bt.peer;

import bt.metainfo.Torrent;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class TrackerPeerSourceFactory implements PeerSourceFactory {

    private ITrackerService trackerService;
    private Map<Torrent, TrackerPeerSource> peerSources;

    @Inject
    public TrackerPeerSourceFactory(ITrackerService trackerService) {
        this.trackerService = trackerService;
        peerSources = new ConcurrentHashMap<>();
    }

    @Override
    public PeerSource getPeerSource(Torrent torrent) {
        TrackerPeerSource peerSource = peerSources.get(torrent);
        if (peerSource == null) {
            peerSource = new TrackerPeerSource(trackerService.getTracker(torrent.getAnnounceKey()), torrent);
            TrackerPeerSource existing = peerSources.putIfAbsent(torrent, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
