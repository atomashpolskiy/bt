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

package bt.portmapping.upnp.jetty;

import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.model.Namespace;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.StreamClient;
import org.fourthline.cling.transport.spi.StreamServer;

import javax.inject.Singleton;

@Singleton
public class JettyAsyncUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

    @Override
    protected Namespace createNamespace() {
        return new Namespace("/bt-upnp");
    }

    @Override
    public StreamClient createStreamClient() {
        return new org.fourthline.cling.transport.impl.jetty.StreamClientImpl(
                new org.fourthline.cling.transport.impl.jetty.StreamClientConfigurationImpl(
                        getSyncProtocolExecutorService()
                )
        );
    }

    @Override
    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new org.fourthline.cling.transport.impl.AsyncServletStreamServerImpl(
                new org.fourthline.cling.transport.impl.AsyncServletStreamServerConfigurationImpl(
                        org.fourthline.cling.transport.impl.jetty.JettyServletContainer.INSTANCE,
                        networkAddressFactory.getStreamListenPort()
                )
        );
    }
}
