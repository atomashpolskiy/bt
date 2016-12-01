package bt.tracker;

import bt.BtException;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class TrackerService implements ITrackerService {

    private Map<String, TrackerFactory> trackerFactories;
    private ConcurrentMap<String, Tracker> knownTrackers;

    @Inject
    public TrackerService(Map<String, TrackerFactory> trackerFactories) {
        this.trackerFactories = trackerFactories;
        this.knownTrackers = new ConcurrentHashMap<>();
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return getOrCreateTracker(trackerUrl);
    }

    @Override
    public Tracker getTracker(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            return new MultiTracker(this, announceKey);
        } else {
            return getOrCreateTracker(announceKey.getTrackerUrl());
        }
    }

    private Tracker getOrCreateTracker(String trackerUrl) {
        Tracker tracker = knownTrackers.get(trackerUrl);
        if (tracker == null) {
            tracker = createTracker(trackerUrl);
            Tracker existing = knownTrackers.putIfAbsent(trackerUrl, tracker);
            if (existing != null) {
                tracker = existing;
            }
        }
        return tracker;
    }

    private Tracker createTracker(String trackerUrl) {
        String protocol = getProtocol(trackerUrl);
        TrackerFactory factory = trackerFactories.get(protocol);
        if (factory == null) {
            throw new BtException("Unsupported tracker protocol: " + protocol);
        }
        return factory.getTracker(trackerUrl);
    }

    private String getProtocol(String url) {
        int schemaDelimiterIndex = url.indexOf("://");
        if (schemaDelimiterIndex < 1) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        return url.substring(0, schemaDelimiterIndex);
    }
}
