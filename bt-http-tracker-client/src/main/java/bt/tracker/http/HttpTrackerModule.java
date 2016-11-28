package bt.tracker.http;

import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

/**
 * @since 1.0
 */
public class HttpTrackerModule implements Module {

    @Override
    public void configure(Binder binder) {
        ServiceModule.contributeTrackerFactory(binder).addBinding("http").to(HttpTrackerFactory.class);
    }
}
