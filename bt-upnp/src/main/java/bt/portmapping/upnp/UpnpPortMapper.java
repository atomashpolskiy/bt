/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.portmapping.upnp;

import bt.net.portmapping.PortMapProtocol;
import bt.net.portmapping.PortMapper;
import org.fourthline.cling.support.model.PortMapping;
import org.fourthline.cling.support.model.PortMapping.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Maps network port on gateway device using UPNP service.
 *
 * @since 1.8
 */
@Singleton
public class UpnpPortMapper implements PortMapper {

    private final static Logger LOG = LoggerFactory.getLogger(UpnpPortMapper.class);

    private final UpnpPortMappingServicesRegistrar mappingServicesRegistrar;

    @Inject
    public UpnpPortMapper(final UpnpPortMappingServicesRegistrar mappingServicesRegistrar) {
        this.mappingServicesRegistrar = mappingServicesRegistrar;
    }

    @Override
    public void mapPort(final int port, final String address, final PortMapProtocol protocol, final String mappingDescription) {
        LOG.debug("Mapping port: [{}] for address: [{}] with description: [{}].", port, address, mappingDescription);
        final Protocol resolvedProtocol = resolveProtocol(protocol);
        final PortMapping portMapping = new PortMapping(port, address, resolvedProtocol, mappingDescription);
        mappingServicesRegistrar.registerPortMapping(portMapping);
        LOG.debug("Port: [{}] for address: [{}] with description: [{}] has been mapped.", port, address, mappingDescription);
    }

    private Protocol resolveProtocol(final PortMapProtocol protocol) {
        switch (protocol) {
            case TCP:
                return Protocol.TCP;
            case UDP:
                return Protocol.UDP;
        }
        throw new IllegalArgumentException("Somehow there's unknown protocol type for port mapping.");
    }
}
