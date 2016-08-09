package bt.tracker;

import bt.service.IdService;
import com.google.inject.Inject;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TrackerService implements ITrackerService {

    private IdService idService;
    private ConcurrentMap<URL, Tracker> knownTrackers;

    @Inject
    public TrackerService(IdService idService) {
        this.idService = idService;
        knownTrackers = new ConcurrentHashMap<>();
    }

    @Override
    public Tracker getTracker(URL baseUrl) {

        Tracker tracker = knownTrackers.get(baseUrl);
        if (tracker == null) {
            tracker = new HttpTracker(baseUrl, idService);
            Tracker oldTracker = knownTrackers.putIfAbsent(baseUrl, tracker);
            tracker = (oldTracker == null) ? tracker : oldTracker;
        }
        return tracker;
    }
}
