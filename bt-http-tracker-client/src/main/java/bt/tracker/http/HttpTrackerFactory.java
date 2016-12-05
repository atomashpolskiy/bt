package bt.tracker.http;

import bt.peer.IPeerRegistry;
import bt.service.IdentityService;
import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;
import com.google.inject.Inject;

/**
 * Creates HTTP tracker clients.
 *
 * @since 1.0
 */
public class HttpTrackerFactory implements TrackerFactory {

    private IdentityService idService;
    private IPeerRegistry peerRegistry;

    @Inject
    public HttpTrackerFactory(IdentityService idService, IPeerRegistry peerRegistry) {
        this.idService = idService;
        this.peerRegistry = peerRegistry;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new HttpTracker(trackerUrl, idService, peerRegistry);
    }
}
