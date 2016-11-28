package bt.tracker.http;

import bt.service.IdService;
import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;
import com.google.inject.Inject;

/**
 * Creates HTTP trackers.
 *
 * @since 1.0
 */
public class HttpTrackerFactory implements TrackerFactory {

    private IdService idService;

    @Inject
    public HttpTrackerFactory(IdService idService) {
        this.idService = idService;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new HttpTracker(trackerUrl, idService);
    }
}
