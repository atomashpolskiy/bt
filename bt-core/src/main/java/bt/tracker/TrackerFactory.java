package bt.tracker;

public interface TrackerFactory {

    Tracker getTracker(String trackerUrl);
}
