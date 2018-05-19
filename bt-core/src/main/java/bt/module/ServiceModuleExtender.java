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

package bt.module;

import bt.net.PeerConnectionAcceptor;
import bt.net.portmapping.PortMapper;
import bt.peer.PeerSourceFactory;
import bt.tracker.TrackerFactory;
import com.google.inject.Binder;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.Objects;

/**
 * Used for contributing custom extensions to {@link ServiceModule}
 *
 * @since 1.5
 */
public class ServiceModuleExtender {

    private final Binder binder;
    private Multibinder<PeerSourceFactory> peerSourceFactories;
    private MapBinder<String, TrackerFactory> trackerFactories;
    private Multibinder<Object> messagingAgents;
    private Multibinder<PeerConnectionAcceptor> connectionAcceptors;
    private Multibinder<PortMapper> portMappers;

    ServiceModuleExtender(Binder binder) {
        this.binder = binder;
    }

    ServiceModuleExtender initAllExtensions() {
        contributePeerSourceFactories();
        contributeTrackerFactories();
        contributeMessagingAgents();
        contributeConnectionAcceptors();

        return this;
    }

    /**
     * Add a peer source factory type.
     *
     * @since 1.5
     */
    public ServiceModuleExtender addPeerSourceFactory(Class<? extends PeerSourceFactory> factoryType) {
        Objects.requireNonNull(factoryType);

        contributePeerSourceFactories().addBinding().to(factoryType).in(Singleton.class);
        return this;
    }

    /**
     * Add a peer source factory instance.
     *
     * @since 1.5
     */
    public ServiceModuleExtender addPeerSourceFactory(PeerSourceFactory factory) {
        Objects.requireNonNull(factory);

        contributePeerSourceFactories().addBinding().toInstance(factory);
        return this;
    }

    /**
     * Contribute a tracker factory type for some URL protocol(s).
     *
     * @since 1.5
     */
    public ServiceModuleExtender addTrackerFactory(Class<? extends TrackerFactory> factoryType,
                                                   String protocol,
                                                   String... otherProtocols) {
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(otherProtocols);
        Objects.requireNonNull(factoryType);

        contributeTrackerFactories().addBinding(protocol).to(factoryType).in(Singleton.class);
        for (String otherProtocol : otherProtocols) {
            Objects.requireNonNull(otherProtocol);
            contributeTrackerFactories().addBinding(otherProtocol).to(factoryType).in(Singleton.class);
        }
        return this;
    }

    /**
     * Contribute a tracker factory instance for some URL protocol(s).
     *
     * @since 1.5
     */
    public ServiceModuleExtender addTrackerFactory(TrackerFactory factory,
                                                   String protocol,
                                                   String... otherProtocols) {
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(otherProtocols);
        Objects.requireNonNull(factory);

        contributeTrackerFactories().addBinding(protocol).toInstance(factory);
        for (String otherProtocol : otherProtocols) {
            Objects.requireNonNull(otherProtocol);
            contributeTrackerFactories().addBinding(otherProtocol).toInstance(factory);
        }
        return this;
    }

    /**
     * Contribute a messaging agent type.
     *
     * @since 1.5
     */
    // use a different method name than overloaded version
    // to avoid accidental contribution of agent type as an actual agent instance
    public ServiceModuleExtender addMessagingAgentType(Class<?> agentType) {
        Objects.requireNonNull(agentType);

        contributeMessagingAgents().addBinding().to(agentType).in(Singleton.class);
        return this;
    }

    /**
     * Contribute a messaging agent instance.
     *
     * @since 1.5
     */
    public ServiceModuleExtender addMessagingAgent(Object agent) {
        Objects.requireNonNull(agent);

        contributeMessagingAgents().addBinding().toInstance(agent);
        return this;
    }

    /**
     * Contribute a peer connection acceptor type.
     *
     * @since 1.6
     */
    public ServiceModuleExtender addConnectionAcceptor(Class<? extends PeerConnectionAcceptor> connectionAcceptorType) {
        Objects.requireNonNull(connectionAcceptorType);

        contributeConnectionAcceptors().addBinding().to(connectionAcceptorType);
        return this;
    }

    /**
     * Contribute a peer connection acceptor.
     *
     * @since 1.6
     */
    public ServiceModuleExtender addConnectionAcceptor(PeerConnectionAcceptor connectionAcceptor) {
        Objects.requireNonNull(connectionAcceptor);

        contributeConnectionAcceptors().addBinding().toInstance(connectionAcceptor);
        return this;
    }

    /**
     * Contribute a port mapper instance.
     *
     * @since 1.8
     */
    public ServiceModuleExtender addPortMapper(PortMapper portMapper){
        Objects.requireNonNull(portMapper);

        contributePortMapper().addBinding().toInstance(portMapper);
        return this;
    }

    /**
     * Contribute a port mapper type.
     *
     * @since 1.8
     */
    public ServiceModuleExtender addPortMapper(Class<? extends PortMapper> portMapperType){
        Objects.requireNonNull(portMapperType);

        contributePortMapper().addBinding().to(portMapperType);
        return this;
    }

    private Multibinder<PortMapper> contributePortMapper() {
        if (portMappers == null){
            portMappers = Multibinder.newSetBinder(binder, PortMapper.class);
        }
        return portMappers;
    }

    private Multibinder<PeerSourceFactory> contributePeerSourceFactories() {
        if (peerSourceFactories == null) {
            peerSourceFactories = Multibinder.newSetBinder(binder, PeerSourceFactory.class);
        }
        return peerSourceFactories;
    }

    private MapBinder<String, TrackerFactory> contributeTrackerFactories() {
        if (trackerFactories == null) {
            trackerFactories = MapBinder.newMapBinder(binder, String.class, TrackerFactory.class, TrackerFactories.class);
        }
        return trackerFactories;
    }

    private Multibinder<Object> contributeMessagingAgents() {
        if (messagingAgents == null) {
            messagingAgents = Multibinder.newSetBinder(binder, Object.class, MessagingAgents.class);
        }
        return messagingAgents;
    }

    private Multibinder<PeerConnectionAcceptor> contributeConnectionAcceptors() {
        if (connectionAcceptors == null) {
            connectionAcceptors = Multibinder.newSetBinder(binder, PeerConnectionAcceptor.class);
        }
        return connectionAcceptors;
    }
}
