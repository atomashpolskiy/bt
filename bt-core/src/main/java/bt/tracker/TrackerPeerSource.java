package bt.tracker;

import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.service.PeerSource;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TrackerPeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerPeerSource.class);

    private ITrackerService trackerService;

    @Inject
    public TrackerPeerSource(ITrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public Collection<Peer> getPeersForTorrent(Torrent torrent) {

        Tracker tracker = trackerService.getTracker(torrent.getTrackerUrl());
        TrackerResponse response = tracker.request(torrent).query();

        if (response.isSuccess()) {
            List<Peer> peers = new ArrayList<>();
            response.getPeers().forEach(peers::add);
            return peers;
        } else {
            LOGGER.error("Failed to get peers for torrent -- " +
                    "unexpected error during interaction with the tracker: " + response.getErrorMessage());
            return Collections.emptyList();
        }
    }
}
