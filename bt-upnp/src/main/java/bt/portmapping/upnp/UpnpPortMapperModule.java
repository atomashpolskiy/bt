/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.module.ServiceModule;
import bt.portmapping.upnp.jetty.JettyAsyncUpnpServiceConfiguration;
import com.google.inject.AbstractModule;
import org.fourthline.cling.UpnpServiceConfiguration;

/**
 * @since 1.8
 */
public class UpnpPortMapperModule extends AbstractModule {

    @Override
    protected void configure() {
        ServiceModule.extend(binder()).addPortMapper(UpnpPortMapper.class);

        bind(UpnpServiceConfiguration.class).to(JettyAsyncUpnpServiceConfiguration.class);
        bind(UpnpPortMappingServicesRegistrar.class);
    }
}
