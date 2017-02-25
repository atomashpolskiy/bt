package bt.dht;

import bt.module.BtModuleProvider;
import com.google.inject.Module;

public class DHTModuleProvider implements BtModuleProvider {

    @Override
    public Module module() {
        return new DHTModule();
    }
}
