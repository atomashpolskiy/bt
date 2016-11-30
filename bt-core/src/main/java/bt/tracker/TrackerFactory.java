package bt.tracker;

/**
 * Creates tracker clients.
 *
 * @since 1.0
 */
public interface TrackerFactory {

    /**
     * Create a client for the tracker, identified by its' URL
     *
     * @param trackerUrl String representation of the tracker's URL
     * @return Tracker client
     * @since 1.0
     */
    Tracker getTracker(String trackerUrl);
}
