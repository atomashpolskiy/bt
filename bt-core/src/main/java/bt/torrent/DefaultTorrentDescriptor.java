package bt.torrent;

import bt.BtException;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.metainfo.Torrent;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultTorrentDescriptor implements TorrentDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTorrentDescriptor.class);

    private Tracker tracker;
    private Torrent torrent;
    private DataDescriptor dataDescriptor;

    private volatile boolean active;

    public DefaultTorrentDescriptor(ITrackerService trackerService, Torrent torrent, DataDescriptor dataDescriptor) {

        this.tracker = trackerService.getTracker(torrent.getAnnounceKey());
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

        dataDescriptor.getChunkDescriptors().forEach(ChunkDescriptor::verify);

        TrackerResponse response = tracker.request(torrent).start();
        if (!response.isSuccess()) {
            if (response.getError().isPresent()) {
                throw new BtException("Failed to start torrent -- " +
                        "unexpected error during interaction with the tracker", response.getError().get());
            } else {
                throw new BtException("Failed to start torrent -- " +
                        "unexpected error during interaction with the tracker; message: " + response.getErrorMessage());
            }
        }

        active = true;
    }

    @Override
    public void stop() {

        if (!active) {
            return;
        }

        active = false;

        processResponse(tracker.request(torrent).stop());
    }

    @Override
    public void complete() {
        processResponse(tracker.request(torrent).complete());
    }

    private void processResponse(TrackerResponse response) {

        if (response.isSuccess()) {
            return;
        }

        if (response.getError().isPresent()) {
            throw new BtException("Unexpected error during interaction with the tracker", response.getError().get());
        } else {
            LOGGER.warn("Unexpected error during interaction with the tracker; message: " + response.getErrorMessage());
        }
    }

    @Override
    public DataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }
}
