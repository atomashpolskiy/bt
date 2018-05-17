package bt.net.portmapping.impl;

import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class PortMappingModule implements Module {

    @Override
    public void configure(Binder binder) {
        ServiceModule.extend(binder).addPortMapper(NoOpPortMapper.class);
    }
}
