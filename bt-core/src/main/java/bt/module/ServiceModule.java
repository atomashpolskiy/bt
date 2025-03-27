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

package bt.module;

import bt.data.ChunkVerifier;
import bt.data.DataDescriptorFactory;
import bt.data.DataReaderFactory;
import bt.data.DefaultChunkVerifier;
import bt.data.IDataDescriptorFactory;
import bt.data.digest.Digester;
import bt.data.digest.SHA1Digester;
import bt.event.EventBus;
import bt.event.EventSink;
import bt.event.EventSource;
import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.ConnectionSource;
import bt.net.DataReceiver;
import bt.net.DataReceivingLoop;
import bt.net.IConnectionHandlerFactory;
import bt.net.IConnectionSource;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionFactory;
import bt.net.IPeerConnectionPool;
import bt.net.MessageDispatcher;
import bt.net.PeerConnectionFactory;
import bt.net.PeerConnectionPool;
import bt.net.SharedSelector;
import bt.net.SocketChannelConnectionAcceptor;
import bt.net.buffer.BufferManager;
import bt.net.buffer.IBufferManager;
import bt.net.pipeline.BufferedPieceRegistry;
import bt.net.pipeline.ChannelPipelineFactory;
import bt.net.pipeline.IBufferedPieceRegistry;
import bt.net.pipeline.IChannelPipelineFactory;
import bt.net.portmapping.impl.PortMappingInitializer;
import bt.peer.IPeerRegistry;
import bt.peer.PeerRegistry;
import bt.peer.PeerSourceFactory;
import bt.processor.ProcessorFactory;
import bt.processor.TorrentProcessorFactory;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.service.ApplicationService;
import bt.service.ClasspathApplicationService;
import bt.service.ExecutorServiceProvider;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IRuntimeLifecycleBinder.LifecycleEvent;
import bt.service.IdentityService;
import bt.service.LifecycleBinding;
import bt.service.RuntimeLifecycleBinder;
import bt.service.VersionAwareIdentityService;
import bt.torrent.AdhocTorrentRegistry;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.BlockCache;
import bt.torrent.data.DataWorker;
import bt.torrent.data.DefaultDataWorker;
import bt.torrent.data.NoCache;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerFactory;
import bt.tracker.TrackerService;
import bt.tracker.udp.UdpTrackerFactory;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

/**
 * This module contributes all core services,
 * shared among all clients attached to a runtime.
 *
 * @since 1.0
 */
public class ServiceModule implements Module {

    /**
     * Returns the extender for contributing custom extensions to the ServiceModule.
     * Should be invoked from the dependent Module's {@link Module#configure(Binder)} method.
     *
     * @param binder DI binder passed to the Module that invokes this method.
     * @return Extender for contributing custom extensions
     * @since 1.5
     */
    public static ServiceModuleExtender extend(Binder binder) {
        return new ServiceModuleExtender(binder);
    }

    /**
     * Contribute a peer source factory.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ServiceModuleExtender#addPeerSourceFactory(Class)} and its' overloaded versions
     */
    @Deprecated
    public static Multibinder<PeerSourceFactory> contributePeerSourceFactory(Binder binder) {
        return Multibinder.newSetBinder(binder, PeerSourceFactory.class);
    }

    /**
     * Contribute a messaging agent.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ServiceModuleExtender#addMessagingAgentType(Class)}
     *             and {@link ServiceModuleExtender#addMessagingAgent(Object)}
     */
    @Deprecated
    public static Multibinder<Object> contributeMessagingAgent(Binder binder) {
        return Multibinder.newSetBinder(binder, Object.class, MessagingAgents.class);
    }

    /**
     * Contribute a tracker factory for some protocol.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ServiceModuleExtender#addTrackerFactory(Class, String, String...)}
     *             and its' overloaded versions
     */
    @Deprecated
    public static MapBinder<String, TrackerFactory> contributeTrackerFactory(Binder binder) {
        return MapBinder.newMapBinder(binder, String.class, TrackerFactory.class, TrackerFactories.class);
    }

    private Config config;

    public ServiceModule() {
        this.config = new Config();
    }

    public ServiceModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(Config.class).toInstance(config);

        ServiceModule.extend(binder).initAllExtensions()
                .addTrackerFactory(UdpTrackerFactory.class, "udp")
                .addConnectionAcceptor(SocketChannelConnectionAcceptor.class);

        // core services that contribute startup lifecycle bindings and should be instantiated eagerly
        binder.bind(BlockCache.class).to(NoCache.class).asEagerSingleton();
        binder.bind(IMessageDispatcher.class).to(MessageDispatcher.class).asEagerSingleton();
        binder.bind(IConnectionSource.class).to(ConnectionSource.class).asEagerSingleton();
        binder.bind(IPeerConnectionPool.class).to(PeerConnectionPool.class).asEagerSingleton();
        binder.bind(IPeerRegistry.class).to(PeerRegistry.class).asEagerSingleton();
        binder.bind(DataReceiver.class).to(DataReceivingLoop.class).asEagerSingleton();

        // other services
        binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
        binder.bind(ApplicationService.class).to(ClasspathApplicationService.class).in(Singleton.class);
        binder.bind(IdentityService.class).to(VersionAwareIdentityService.class).in(Singleton.class);
        binder.bind(ITrackerService.class).to(TrackerService.class).in(Singleton.class);
        binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
        binder.bind(TorrentRegistry.class).to(AdhocTorrentRegistry.class).in(Singleton.class);
        binder.bind(IRuntimeLifecycleBinder.class).to(RuntimeLifecycleBinder.class).in(Singleton.class);
        binder.bind(ProcessorFactory.class).to(TorrentProcessorFactory.class).in(Singleton.class);
        binder.bind(IBufferManager.class).to(BufferManager.class).in(Singleton.class);
        binder.bind(IChannelPipelineFactory.class).to(ChannelPipelineFactory.class).in(Singleton.class);
        binder.bind(IBufferedPieceRegistry.class).to(BufferedPieceRegistry.class).in(Singleton.class);

        // single instance of event bus provides two different injectable services
        binder.bind(EventSink.class).to(EventBus.class).in(Singleton.class);
        binder.bind(EventSource.class).to(EventBus.class).in(Singleton.class);

        binder.bind(ExecutorService.class).annotatedWith(ClientExecutor.class)
                .toProvider(ExecutorServiceProvider.class).in(Singleton.class);

        binder.bind(PortMappingInitializer.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public Digester provideDigester(Config config) {
        return SHA1Digester.newDigester(config.getHashingBufferSize());
    }

    @Provides
    @Singleton
    public ChunkVerifier provideVerifier(Config config, Digester digester) {
        return new DefaultChunkVerifier(digester, config.getNumOfHashingThreads());
    }

    @Provides
    @Singleton
    public IDataDescriptorFactory provideDataDescriptorFactory(Config config, EventSource eventSource, ChunkVerifier verifier) {
        DataReaderFactory dataReaderFactory = new DataReaderFactory(eventSource);
        return new DataDescriptorFactory(dataReaderFactory, verifier, config.getTransferBlockSize());
    }

    @Provides
    @Singleton
    public DataWorker provideDataWorker(
            IRuntimeLifecycleBinder lifecycleBinder,
            TorrentRegistry torrentRegistry,
            ChunkVerifier verifier,
            BlockCache blockCache,
            Config config) {
        return new DefaultDataWorker(lifecycleBinder, torrentRegistry, verifier, blockCache, config);
    }

    @Provides
    @Singleton
    public EventBus provideEventBus() {
        return new EventBus();
    }

    @Provides
    @Singleton
    @PeerConnectionSelector
    public Selector provideSelector(IRuntimeLifecycleBinder lifecycleBinder) {
        Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get I/O selector", e);
        }

        Runnable shutdownRoutine = () -> {
            try {
                selector.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close selector", e);
            }
        };
        lifecycleBinder.addBinding(LifecycleEvent.SHUTDOWN,
                LifecycleBinding.bind(shutdownRoutine).description("Shutdown selector").build());

        return selector;
    }

    @Provides
    @Singleton
    @PeerRegistrationQueue
    public ConcurrentLinkedQueue<PeerRegistrationEvent> provideRegistrationQueue(IRuntimeLifecycleBinder lifecycleBinder) {
        return new ConcurrentLinkedQueue<>();
    }

    @Provides
    @Singleton
    public IPeerConnectionFactory providePeerConnectionFactory(
            @PeerConnectionSelector Selector selector,
            IConnectionHandlerFactory connectionHandlerFactory,
            @BitTorrentProtocol MessageHandler<Message> bittorrentProtocol,
            TorrentRegistry torrentRegistry,
            IChannelPipelineFactory channelPipelineFactory,
            IBufferManager bufferManager,
            DataReceiver dataReceiver,
            EventSource eventSource,
            Config config) {
        return new PeerConnectionFactory(selector, connectionHandlerFactory, channelPipelineFactory,
                bittorrentProtocol, torrentRegistry, bufferManager, dataReceiver, eventSource, config);
    }

    @Provides
    @Singleton
    public SocketChannelConnectionAcceptor provideSocketChannelConnectionAcceptor(
            @PeerConnectionSelector Selector selector,
            IPeerConnectionFactory connectionFactory,
            Config config) {
        InetSocketAddress localAddress = new InetSocketAddress(config.getAcceptorAddress(), config.getAcceptorPort());
        return new SocketChannelConnectionAcceptor(selector, connectionFactory, localAddress);
    }
}
