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
 *
 * @since 1.8
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
