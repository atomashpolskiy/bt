package bt.portmapping.upnp;

import bt.service.IRuntimeLifecycleBinder;
import bt.service.LifecycleBinding;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.support.igd.PortMappingListener;
import org.fourthline.cling.support.model.PortMapping;

import javax.inject.Inject;
import javax.inject.Singleton;

import static bt.service.IRuntimeLifecycleBinder.LifecycleEvent.SHUTDOWN;

/**
 * Creates UPNP service, and binds its lifecycle to the app's one.
 */
@Singleton
public class UpnpPortMappingServicesRegistrar {

    private final IRuntimeLifecycleBinder lifecycleBinder;

    @Inject
    public UpnpPortMappingServicesRegistrar(IRuntimeLifecycleBinder lifecycleBinder) {
        this.lifecycleBinder = lifecycleBinder;
    }

    /**
     * Creates corresponding UPNP serive for port mapping.
     *
     * @param portMapping desired port mapping;
     */
    public void registerPortMapping(PortMapping portMapping) {
        final UpnpServiceImpl service = new UpnpServiceImpl(new PortMappingListener(portMapping));
        service.getControlPoint().search();
        bindShutdownHook(service);
    }

    private void bindShutdownHook(UpnpServiceImpl service) {
        lifecycleBinder.addBinding(SHUTDOWN,
                LifecycleBinding.bind(service::shutdown)
                        .description("Disables port mapping on application shutdown.")
                        .async()
                        .build());
    }
}
