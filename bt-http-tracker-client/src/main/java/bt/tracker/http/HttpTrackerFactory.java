package bt.tracker.http;

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

    @Inject
    public HttpTrackerFactory(IdentityService idService) {
        this.idService = idService;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new HttpTracker(trackerUrl, idService);
    }
}
