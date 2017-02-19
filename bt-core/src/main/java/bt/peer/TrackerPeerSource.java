package bt.peer;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

class TrackerPeerSource extends ScheduledPeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerPeerSource.class);

    private Tracker tracker;
    private Torrent torrent;
    private Duration trackerQueryInterval;

    private volatile long lastRefreshed;

    TrackerPeerSource(ExecutorService executor, Tracker tracker, Torrent torrent, Duration trackerQueryInterval) {
        super(executor);
        this.tracker = tracker;
        this.torrent = torrent;
        this.trackerQueryInterval = trackerQueryInterval;
    }

    @Override
    protected Collection<Peer> collectPeers() {
        Collection<Peer> peers = null;
        if (System.currentTimeMillis() - lastRefreshed >= trackerQueryInterval.toMillis()) {
            TrackerResponse response = tracker.request(torrent).query();
            if (response.isSuccess()) {
                peers = new HashSet<>();
                response.getPeers().forEach(peers::add);
            } else {
                if (response.getError().isPresent()) {
                    throw new BtException("Failed to get peers for torrent", response.getError().get());
                } else {
                    LOGGER.error("Failed to get peers for torrent -- " +
                            "unexpected error during interaction with the tracker; message: " + response.getErrorMessage());
                }
            }
        }
        lastRefreshed = System.currentTimeMillis();
        return peers == null ? Collections.emptySet() : peers;
    }

    @Override
    public String toString() {
        return "TrackerPeerSource {" + tracker + "}";
    }
}
