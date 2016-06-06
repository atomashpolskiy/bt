package bt.torrent;

import bt.BtException;
import bt.data.IDataDescriptor;
import bt.metainfo.Torrent;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorrentDescriptor implements ITorrentDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentDescriptor.class);

    private Tracker tracker;
    private Torrent torrent;
    private IDataDescriptor dataDescriptor;

    private volatile TrackerResponse lastResponse;

    private volatile boolean active;
    private volatile long lastTrackerQueryTime;

    public TorrentDescriptor(ITrackerService trackerService, Torrent torrent, IDataDescriptor dataDescriptor) {

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
    public IDataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }
}
