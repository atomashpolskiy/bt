package bt.tracker;

import bt.metainfo.Torrent;
import bt.service.PeerSource;
import bt.service.PeerSourceFactory;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrackerPeerSourceFactory implements PeerSourceFactory {

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
            peerSource = new TrackerPeerSource(trackerService.getTracker(torrent.getTrackerUrl()), torrent);
            TrackerPeerSource existing = peerSources.putIfAbsent(torrent, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
