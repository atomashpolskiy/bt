package bt.tracker.http;

import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

/**
 * Provides support for integration with HTTP trackers.
 *
 * @since 1.0
 */
public class HttpTrackerModule implements Module {

    @Override
    public void configure(Binder binder) {
        ServiceModule.extend(binder).addTrackerFactory(HttpTrackerFactory.class, "http", "https");
    }
}
