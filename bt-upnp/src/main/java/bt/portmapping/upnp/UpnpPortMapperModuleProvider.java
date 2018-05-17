package bt.portmapping.upnp;

import bt.module.BtModuleProvider;
import com.google.inject.Module;

public class UpnpPortMapperModuleProvider implements BtModuleProvider {

    @Override
    public Module module() {
        return new UpnpPortMapperModule();
    }
}
