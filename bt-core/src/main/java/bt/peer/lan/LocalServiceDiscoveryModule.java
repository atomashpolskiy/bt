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

package bt.peer.lan;

import bt.module.PeerConnectionSelector;
import bt.module.ServiceModule;
import bt.net.PeerConnectionAcceptor;
import bt.net.SharedSelector;
import bt.net.SocketChannelConnectionAcceptor;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @since 1.6
 */
public class LocalServiceDiscoveryModule implements Module {

    private final LocalServiceDiscoveryConfig config;

    /**
     * @since 1.6
     */
    public LocalServiceDiscoveryModule() {
        this.config = new LocalServiceDiscoveryConfig();
    }

    /**
     * @since 1.6
     */
    public LocalServiceDiscoveryModule(LocalServiceDiscoveryConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(LocalServiceDiscoveryConfig.class).toInstance(config);

        ServiceModule.extend(binder).addPeerSourceFactory(LocalServiceDiscoveryPeerSourceFactory.class);

        // core service that binds to runtime events and should be instantiated eagerly
        binder.bind(ILocalServiceDiscoveryService.class).to(LocalServiceDiscoveryService.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public Cookie provideLocalServiceDiscoveryCookie() {
        return Cookie.newCookie();
    }

    @Provides
    @Singleton
    public ILocalServiceDiscoveryInfo provideLocalServiceDiscoveryInfo(
            Set<PeerConnectionAcceptor> connectionAcceptors,
            LocalServiceDiscoveryConfig config) {

        Set<SocketChannelConnectionAcceptor> socketAcceptors = connectionAcceptors.stream()
                .filter(a -> a instanceof SocketChannelConnectionAcceptor)
                .map(a -> (SocketChannelConnectionAcceptor)a)
                .collect(Collectors.toSet());

        return new LocalServiceDiscoveryInfo(socketAcceptors, config.getLocalServiceDiscoveryAnnounceGroups());
    }

    @Provides
    @Singleton
    public Collection<AnnounceGroupChannel> provideGroupChannels(
            ILocalServiceDiscoveryInfo info,
            @PeerConnectionSelector SharedSelector selector,
            IRuntimeLifecycleBinder lifecycleBinder) {

        Collection<AnnounceGroupChannel> groupChannels = info.getCompatibleGroups().stream()
                .map(g -> new AnnounceGroupChannel(g, selector, info.getNetworkInterfaces()))
                .collect(Collectors.toList());

        lifecycleBinder.onShutdown(() -> {
            groupChannels.forEach(AnnounceGroupChannel::closeQuietly);
        });

        return groupChannels;
    }
}
