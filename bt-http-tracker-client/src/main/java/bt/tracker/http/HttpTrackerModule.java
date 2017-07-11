package bt.tracker.http;

import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Singleton;

/**
 * Provides support for integration with HTTP trackers.
 *
 * @since 1.0
 */
public class HttpTrackerModule implements Module {

    @Override
    public void configure(Binder binder) {
        ServiceModule.contributeTrackerFactory(binder).addBinding("http").to(HttpTrackerFactory.class).in(Singleton.class);
        ServiceModule.contributeTrackerFactory(binder).addBinding("https").to(HttpTrackerFactory.class).in(Singleton.class);
    }
}
