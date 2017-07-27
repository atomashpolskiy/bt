package bt.tracker;

import bt.metainfo.TorrentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @since 1.3
 */
public class TrackerAnnouncer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerAnnouncer.class);

    private enum Event {
        start, stop, complete
    }

    private final Optional<Tracker> trackerOptional;
    private final TorrentId torrentId;

    public TrackerAnnouncer(ITrackerService trackerService, TorrentId torrentId, AnnounceKey announceKey) {
        this.trackerOptional = Optional.ofNullable(createTracker(trackerService, torrentId, announceKey));
        this.torrentId = torrentId;
    }

    private Tracker createTracker(ITrackerService trackerService, TorrentId torrentId, AnnounceKey announceKey) {
        try {
            String trackerUrl = getTrackerUrl(announceKey);
            if (trackerService.isSupportedProtocol(trackerUrl)) {
                return trackerService.getTracker(announceKey);
            } else {
                LOGGER.warn("Tracker URL protocol is not supported: " + trackerUrl);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create tracker for announce key: " + announceKey);
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
                processResponse(Event.start, tracker, tracker.request(torrentId).start());
            } catch (Exception e) {
                logTrackerError(Event.start, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void stop() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.stop, tracker, tracker.request(torrentId).stop());
            } catch (Exception e) {
                logTrackerError(Event.stop, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void complete() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.complete, tracker, tracker.request(torrentId).complete());
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
