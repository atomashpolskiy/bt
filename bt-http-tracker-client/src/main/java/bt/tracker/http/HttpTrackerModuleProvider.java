package bt.tracker.http;

import bt.module.BtModuleProvider;
import com.google.inject.Module;

/**
 * @since 1.1
 */
public class HttpTrackerModuleProvider implements BtModuleProvider {

    @Override
    public Module module() {
        return new HttpTrackerModule();
    }
}
