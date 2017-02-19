package bt.torrent;

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

    private enum Event {
        start, stop, complete
    }

    private Optional<Tracker> trackerOptional;
    private Torrent torrent;
    private DataDescriptor dataDescriptor;

    private volatile boolean active;

    public DefaultTorrentDescriptor(ITrackerService trackerService, Torrent torrent, DataDescriptor dataDescriptor) {
        this.trackerOptional = Optional.ofNullable(createTracker(trackerService, torrent));
        this.torrent = torrent;
        this.dataDescriptor = dataDescriptor;
    }

    private Tracker createTracker(ITrackerService trackerService, Torrent torrent) {
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKeyOptional();
        if (announceKey.isPresent()) {
            try {
                String trackerUrl = getTrackerUrl(announceKey.get());
                if (trackerService.isSupportedProtocol(trackerUrl)) {
                    return trackerService.getTracker(announceKey.get());
                } else {
                    LOGGER.warn("Tracker URL protocol is not supported: " + trackerUrl);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to create tracker for announce key: " + announceKey.get());
            }
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

        if (trackerOptional.isPresent()) {
            Tracker tracker = trackerOptional.get();
            try {
                processResponse(Event.start, tracker, tracker.request(torrent).start());
            } catch (Exception e) {
                logTrackerError(Event.start, tracker, Optional.of(e), Optional.empty());
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

        if (trackerOptional.isPresent()) {
            Tracker tracker = trackerOptional.get();
            try {
                processResponse(Event.stop, tracker, tracker.request(torrent).stop());
            } catch (Exception e) {
                logTrackerError(Event.stop, tracker, Optional.of(e), Optional.empty());
            }
        }
    }

    @Override
    public void complete() {
        if (trackerOptional.isPresent()) {
            Tracker tracker = trackerOptional.get();
            try {
                processResponse(Event.complete, tracker, tracker.request(torrent).complete());
            } catch (Exception e) {
                logTrackerError(Event.complete, tracker, Optional.of(e), Optional.empty());
            }
        }
    }

    private void processResponse(Event event, Tracker tracker, TrackerResponse response) {
        if (!response.isSuccess()) {
            logTrackerError(event, tracker, response.getError(), Optional.ofNullable(response.getErrorMessage()));
        }
    }

    private void logTrackerError(Event event, Tracker tracker, Optional<Throwable> e, Optional<String> message) {
        String log = String.format("Failed to announce '%s' event due to unexpected error " +
                "during interaction with the tracker: %s", event.name(), tracker);
        if (message.isPresent()) {
            log += "; message: " + message;
        }

        if (e.isPresent()) {
            LOGGER.error(log, e);
        } else {
            LOGGER.warn(log);
        }
    }

    @Override
    public DataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }
}
