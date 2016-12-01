package bt.peer;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class TrackerPeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerPeerSource.class);

    private Tracker tracker;
    private Torrent torrent;
    private Duration trackerQueryInterval;

    private volatile Collection<Peer> peers;
    private volatile long lastRefreshed;
    private final Object lock;

    TrackerPeerSource(Tracker tracker, Torrent torrent, Duration trackerQueryInterval) {
        this.tracker = tracker;
        this.torrent = torrent;
        this.trackerQueryInterval = trackerQueryInterval;
        this.peers = Collections.emptyList();
        this.lock = new Object();
    }

    @Override
    public boolean update() {

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshed >= trackerQueryInterval.toMillis()) {

            synchronized (lock) {
                if (currentTime - lastRefreshed >= trackerQueryInterval.toMillis()) {

                    TrackerResponse response = tracker.request(torrent).query();
                    lastRefreshed = System.currentTimeMillis();

                    if (response.isSuccess()) {
                        List<Peer> peers = new ArrayList<>();
                        response.getPeers().forEach(peers::add);
                        this.peers = peers;
                        return true;
                    } else {
                        if (response.getError().isPresent()) {
                            throw new BtException("Failed to get peers for torrent", response.getError().get());
                        } else {
                            LOGGER.error("Failed to get peers for torrent -- " +
                                    "unexpected error during interaction with the tracker; message: " + response.getErrorMessage());
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
    }
}
