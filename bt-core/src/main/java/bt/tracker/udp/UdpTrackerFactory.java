package bt.tracker.udp;

import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdService;
import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;
import com.google.inject.Inject;

import java.net.URL;

/**
 * Creates UDP trackers.
 *
 * @since 1.0
 */
public class UdpTrackerFactory implements TrackerFactory {

    private IdService idService;
    private IRuntimeLifecycleBinder lifecycleBinder;

    @Inject
    public UdpTrackerFactory(IdService idService, IRuntimeLifecycleBinder lifecycleBinder) {
        this.idService = idService;
        this.lifecycleBinder = lifecycleBinder;
    }

    @Override
    public Tracker getTracker(URL trackerUrl) {
        return new UdpTracker(idService, lifecycleBinder, trackerUrl);
    }
}
