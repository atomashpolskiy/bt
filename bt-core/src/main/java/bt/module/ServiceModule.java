package bt.module;

import bt.data.DataDescriptorFactory;
import bt.data.IDataDescriptorFactory;
import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.net.MessageDispatcher;
import bt.net.PeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.peer.PeerRegistry;
import bt.peer.PeerSourceFactory;
import bt.runtime.Config;
import bt.service.ApplicationService;
import bt.service.ClasspathApplicationService;
import bt.service.ExecutorServiceProvider;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.service.RuntimeLifecycleBinder;
import bt.service.VersionAwareIdentityService;
import bt.torrent.AdhocTorrentRegistry;
import bt.torrent.ITorrentSessionFactory;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.DataWorkerFactory;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.messaging.TorrentSessionFactory;
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

import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * This module contributes all core services,
 * shared among all clients attached to a runtime.
 *
 * @since 1.0
 */
public class ServiceModule implements Module {

    /**
     * Contribute a peer source factory.
     *
     * @since 1.0
     */
    public static Multibinder<PeerSourceFactory> contributePeerSourceFactory(Binder binder) {
        return Multibinder.newSetBinder(binder, PeerSourceFactory.class);
    }

    /**
     * Contribute a messaging agent.
     *
     * @since 1.0
     */
    public static Multibinder<Object> contributeMessagingAgent(Binder binder) {
        return Multibinder.newSetBinder(binder, Object.class, MessagingAgents.class);
    }

    /**
     * Contribute a tracker factory for some protocol.
     *
     * @since 1.0
     */
    public static MapBinder<String, TrackerFactory> contributeTrackerFactory(Binder binder) {
        return MapBinder.newMapBinder(binder, String.class, TrackerFactory.class);
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

        // trigger creation of extension points
        ServiceModule.contributeMessagingAgent(binder);
        ServiceModule.contributePeerSourceFactory(binder);
        ServiceModule.contributeTrackerFactory(binder);

        binder.bind(Config.class).toInstance(config);

        binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
        binder.bind(ApplicationService.class).to(ClasspathApplicationService.class).in(Singleton.class);
        binder.bind(IdentityService.class).to(VersionAwareIdentityService.class).in(Singleton.class);
        binder.bind(ITrackerService.class).to(TrackerService.class).in(Singleton.class);
        binder.bind(TorrentRegistry.class).to(AdhocTorrentRegistry.class).in(Singleton.class);
        binder.bind(IPeerConnectionPool.class).to(PeerConnectionPool.class).in(Singleton.class);
        binder.bind(IMessageDispatcher.class).to(MessageDispatcher.class).in(Singleton.class);
        binder.bind(IRuntimeLifecycleBinder.class).to(RuntimeLifecycleBinder.class).in(Singleton.class);
        binder.bind(ITorrentSessionFactory.class).to(TorrentSessionFactory.class).in(Singleton.class);

        // TODO: register a shutdown hook in the runtime
        binder.bind(ExecutorService.class).annotatedWith(ClientExecutor.class)
                .toProvider(ExecutorServiceProvider.class).in(Singleton.class);

        ServiceModule.contributeTrackerFactory(binder).addBinding("udp").to(UdpTrackerFactory.class);
    }

    @Provides
    @Singleton
    public IDataDescriptorFactory provideDataDescriptorFactory() {
        return new DataDescriptorFactory(config.getTransferBlockSize());
    }

    @Provides
    @Singleton
    public IDataWorkerFactory provideDataWorkerFactory(IRuntimeLifecycleBinder lifecycleBinder) {
        return new DataWorkerFactory(lifecycleBinder, config.getMaxIOQueueSize());
    }

    @Provides
    @Singleton
    public IPeerRegistry providePeerRegistry(IRuntimeLifecycleBinder lifecycleBinder,
                                             IdentityService idService,
                                             ITrackerService trackerService,
                                             Set<PeerSourceFactory> peerSourceFactories) {
        return new PeerRegistry(
                lifecycleBinder, idService, trackerService, peerSourceFactories,
                config.getAcceptorAddress(), config.getAcceptorPort(),
                config.getPeerDiscoveryInterval(), config.getTrackerQueryInterval());
    }
}
