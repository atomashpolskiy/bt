package bt.torrent;

import bt.BtException;
import bt.data.IChunkDescriptor;
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

    private volatile boolean active;

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

        dataDescriptor.getChunkDescriptors().forEach(IChunkDescriptor::verify);

        TrackerResponse response = tracker.request(torrent).start();
        if (response != null && !response.isSuccess()) {
            throw new BtException("Failed to start torrent -- " +
                    "unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }

        active = true;
    }

    @Override
    public void stop() {

        if (!active) {
            return;
        }

        active = false;

        TrackerResponse response = tracker.request(torrent).stop();
        if (response != null && !response.isSuccess()) {
            LOGGER.warn("Unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }
    }

    @Override
    public void complete() {

        TrackerResponse response = tracker.request(torrent).complete();
        if (response != null && !response.isSuccess()) {
            LOGGER.warn("Unexpected error during interaction with the tracker: " + response.getErrorMessage());
        }
    }

    @Override
    public IDataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }
}
