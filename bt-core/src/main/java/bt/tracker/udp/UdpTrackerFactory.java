package bt.tracker.udp;

import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;
import com.google.inject.Inject;

/**
 * Creates UDP tracker clients.
 *
 * @since 1.0
 */
public class UdpTrackerFactory implements TrackerFactory {

    private IdentityService idService;
    private IRuntimeLifecycleBinder lifecycleBinder;

    @Inject
    public UdpTrackerFactory(IdentityService idService, IRuntimeLifecycleBinder lifecycleBinder) {
        this.idService = idService;
        this.lifecycleBinder = lifecycleBinder;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new UdpTracker(idService, lifecycleBinder, trackerUrl);
    }
}
