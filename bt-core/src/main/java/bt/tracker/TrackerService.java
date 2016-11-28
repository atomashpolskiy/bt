package bt.tracker;

import bt.service.IdService;
import bt.tracker.http.HttpTracker;
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
        return getOrCreateTracker(baseUrl);
    }

    @Override
    public Tracker getTracker(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            return new MultiTracker(this, announceKey);
        } else {
            return getOrCreateTracker(announceKey.getTrackerUrl());
        }
    }

    private Tracker getOrCreateTracker(URL baseUrl) {

        Tracker tracker = knownTrackers.get(baseUrl);
        if (tracker == null) {
            tracker = new HttpTracker(baseUrl, idService);
            Tracker oldTracker = knownTrackers.putIfAbsent(baseUrl, tracker);
            tracker = (oldTracker == null) ? tracker : oldTracker;
        }
        return tracker;
    }
}
