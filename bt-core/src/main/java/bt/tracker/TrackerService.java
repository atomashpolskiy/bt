package bt.tracker;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TrackerService implements ITrackerService {

    private ConcurrentMap<URL, Tracker> knownTrackers;

    public TrackerService() {
        knownTrackers = new ConcurrentHashMap<>();
    }

    @Override
    public Tracker getTracker(URL baseUrl) {

        Tracker tracker = knownTrackers.get(baseUrl);
        if (tracker == null) {
            tracker = new HttpTracker(baseUrl);
            Tracker oldTracker = knownTrackers.putIfAbsent(baseUrl, tracker);
            tracker = (oldTracker == null) ? tracker : oldTracker;
        }
        return tracker;
    }
}
