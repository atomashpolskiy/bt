package bt.tracker;

import bt.BtException;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdService;
import com.google.inject.Inject;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TrackerService implements ITrackerService {

    private Map<String, TrackerFactory> trackerFactories;
    private ConcurrentMap<URL, Tracker> knownTrackers;

    @Inject
    public TrackerService(Map<String, TrackerFactory> trackerFactories) {
        this.trackerFactories = trackerFactories;
        this.knownTrackers = new ConcurrentHashMap<>();
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
            tracker = createTracker(baseUrl);
            Tracker existing = knownTrackers.putIfAbsent(baseUrl, tracker);
            if (existing != null) {
                tracker = existing;
            }
        }
        return tracker;
    }

    private Tracker createTracker(URL baseUrl) {
        String protocol = baseUrl.getProtocol();
        TrackerFactory factory = trackerFactories.get(protocol);
        if (factory == null) {
            throw new BtException("Unsupported tracker protocol: " + protocol);
        }
        return factory.getTracker(baseUrl);
    }
}
