package bt.tracker;

import bt.metainfo.Torrent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TrackerAnnouncer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerAnnouncer.class);

    private enum Event {
        start, stop, complete
    }

    private final Optional<Tracker> trackerOptional;
    private final Torrent torrent;

    public TrackerAnnouncer(ITrackerService trackerService, Torrent torrent) {
        this.trackerOptional = Optional.ofNullable(createTracker(trackerService, torrent));
        this.torrent = torrent;
    }

    private Tracker createTracker(ITrackerService trackerService, Torrent torrent) {
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
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

    public void start() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.start, tracker, tracker.request(torrent).start());
            } catch (Exception e) {
                logTrackerError(Event.start, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void stop() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.stop, tracker, tracker.request(torrent).stop());
            } catch (Exception e) {
                logTrackerError(Event.stop, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void complete() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.complete, tracker, tracker.request(torrent).complete());
            } catch (Exception e) {
                logTrackerError(Event.complete, tracker, Optional.of(e), Optional.empty());
            }
        });
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
}
