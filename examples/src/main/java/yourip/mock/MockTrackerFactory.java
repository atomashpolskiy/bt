package yourip.mock;

import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;

public class MockTrackerFactory implements TrackerFactory {
    private static final String schema = "mock";

    public static String schema() {
        return schema;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        if (MockTracker.url().equals(trackerUrl)) {
            return new MockTracker();
        }
        throw new IllegalArgumentException("Unsupported tracker: " + trackerUrl);
    }
}
