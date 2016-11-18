package bt.tracker;

import java.net.URL;

/**
 * This service acts a factory of trackers.
 *
 * @since 1.0
 */
public interface ITrackerService {

    /**
     * Get a single tracker by its' URL
     *
     * @return Single tracker
     * @since 1.0
     */
    Tracker getTracker(URL baseUrl);

    /**
     * Get a tracker by its' announce key
     *
     * @return Either a single-tracker or a multi-tracker,
     *         depending of the type of the announce key
     * @since 1.0
     */
    Tracker getTracker(AnnounceKey announceKey);
}
