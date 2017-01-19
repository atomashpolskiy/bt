package bt.torrent;

import bt.BtException;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.metainfo.Torrent;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

class DefaultTorrentDescriptor implements TorrentDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTorrentDescriptor.class);

    private Optional<Tracker> tracker;
    private Torrent torrent;
    private DataDescriptor dataDescriptor;

    private volatile boolean active;

    public DefaultTorrentDescriptor(ITrackerService trackerService, Torrent torrent, DataDescriptor dataDescriptor) {
        this.tracker = Optional.ofNullable(createTracker(trackerService, torrent));
        this.torrent = torrent;
        this.dataDescriptor = dataDescriptor;
    }

    private Tracker createTracker(ITrackerService trackerService, Torrent torrent) {
        try {
            String trackerUrl = getTrackerUrl(torrent.getAnnounceKey());
            if (trackerService.isSupportedProtocol(trackerUrl)) {
                return trackerService.getTracker(torrent.getAnnounceKey());
            } else {
                LOGGER.warn("Tracker URL protocol is not supported: " + trackerUrl);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create tracker for announce key: " + torrent.getAnnounceKey());
        }
        return null;
    }

    private String getTrackerUrl(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            return announceKey.getTrackerUrls().get(0).get(0);
        } else {
            return announceKey.getTrackerUrl();
        }
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

        if (tracker.isPresent()) {
            try {
                TrackerResponse response = tracker.get().request(torrent).start();
                if (!response.isSuccess()) {
                    if (response.getError().isPresent()) {
                        LOGGER.warn("Failed to announce 'start' event -- " +
                                "unexpected error during interaction with the tracker", response.getError().get());
                    } else {
                        LOGGER.warn("Failed to announce 'start' event -- " +
                                "unexpected error during interaction with the tracker; message: " + response.getErrorMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to announce 'start' event -- " +
                        "unexpected error during interaction with the tracker", e);
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

        if (tracker.isPresent()) {
            try {
                processResponse(tracker.get().request(torrent).stop());
            } catch (Exception e) {
                LOGGER.warn("Failed to announce 'stop' event -- " +
                        "unexpected error during interaction with the tracker", e);
            }
        }
    }

    @Override
    public void complete() {
        if (tracker.isPresent()) {
            try {
                processResponse(tracker.get().request(torrent).complete());
            } catch (Exception e) {
                LOGGER.warn("Failed to announce 'complete' event -- " +
                        "unexpected error during interaction with the tracker", e);
            }
        }
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
