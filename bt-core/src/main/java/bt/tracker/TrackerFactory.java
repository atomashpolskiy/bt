package bt.tracker;

import java.net.URL;

public interface TrackerFactory {

    Tracker getTracker(URL trackerUrl);
}
