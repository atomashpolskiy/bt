package bt.torrent;

import bt.BtException;
import bt.data.IDataDescriptor;
import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.service.IConfigurationService;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class TorrentDescriptor implements ITorrentDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentDescriptor.class);

    private IConfigurationService configurationService;
    private Tracker tracker;
    private Torrent torrent;
    private IDataDescriptor dataDescriptor;

    private volatile TrackerResponse lastResponse;

    private volatile boolean active;
    private volatile long lastTrackerQueryTime;

    public TorrentDescriptor(ITrackerService trackerService, IConfigurationService configurationService,
                             Torrent torrent, IDataDescriptor dataDescriptor) {
        this.configurationService = configurationService;
        this.tracker = trackerService.getTracker(torrent.getTrackerUrl());
        this.torrent = torrent;
        this.dataDescriptor = dataDescriptor;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void start() {

        if (active) {
            return;
        }

        TrackerResponse response = tracker.request(torrent).start();
        if (!response.isSuccess()) {
            throw new BtException("Failed to start torrent -- " +
                    "unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }

        lastResponse = response;
        lastTrackerQueryTime = System.currentTimeMillis();
        active = true;
    }

    @Override
    public void stop() {

        if (!active) {
            return;
        }

        active = false;

        TrackerResponse response = tracker.request(torrent).stop();
        if (!response.isSuccess()) {
            LOGGER.warn("Unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }
    }

    @Override
    public void complete() {

        TrackerResponse response = tracker.request(torrent).complete();
        if (!response.isSuccess()) {
            LOGGER.warn("Unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }
    }

    @Override
    public Iterable<Peer> queryPeers() {

        if (!active) {
            LOGGER.warn("Received a request for peers, but the torrent is not active; " +
                    "will not query for peers from tracker");
            return Collections.emptyList();
        }

        if (!mightQueryTracker()) {
            LOGGER.info("Received a request for peers, but can't query tracker currently; "  +
                    "will return peers from the last tracker's response");
            return lastResponse.getPeers();
        }

        TrackerResponse response = tracker.request(torrent).query();
        if (response.isSuccess()) {
            lastResponse = response;
            lastTrackerQueryTime = System.currentTimeMillis();
        } else {
            LOGGER.warn("Failed to get peers for torrent -- " +
                    "unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }

        return lastResponse.getPeers();
    }

    @Override
    public IDataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }

    private boolean mightQueryTracker() {

        // ignoring tracker's minInterval for now
        return (System.currentTimeMillis() - lastTrackerQueryTime) >= configurationService.getPeerRefreshThreshold();
    }
}
