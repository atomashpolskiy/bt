package bt.portmapping.upnp;

import bt.module.ServiceModule;
import com.google.inject.AbstractModule;

public class UpnpPortMapperModule extends AbstractModule {

    @Override
    protected void configure() {
        ServiceModule.extend(binder()).addPortMapper(UpnpPortMapper.class);

        bind(UpnpPortMappingServicesRegistrar.class);
    }
}
