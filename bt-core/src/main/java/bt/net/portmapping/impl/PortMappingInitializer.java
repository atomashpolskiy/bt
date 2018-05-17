package bt.net.portmapping.impl;

import bt.net.portmapping.PortMapper;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.LifecycleBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.util.Set;

import static bt.net.portmapping.PortMapProtocol.TCP;

/**
 * Initializes port mappings on application startup.
 */
@Singleton
public class PortMappingInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(PortMappingInitializer.class);

    @Inject
    public PortMappingInitializer(Set<PortMapper> portMappers, IRuntimeLifecycleBinder lifecycleBinder, Config config) {
        LOG.info("Mapping application's acceptor port on startup.");

        final int acceptorPort = config.getAcceptorPort();
        final InetAddress acceptorAddress = config.getAcceptorAddress();

        lifecycleBinder.onStartup(LifecycleBinding.bind(() ->
                portMappers.forEach(m -> mapPort(acceptorPort, acceptorAddress, m)))
                .build());
    }

    private void mapPort(int acceptorPort, InetAddress acceptorAddress, PortMapper m) {
        m.mapPort(acceptorPort, acceptorAddress.toString(), TCP, "bt acceptor");
    }
}
