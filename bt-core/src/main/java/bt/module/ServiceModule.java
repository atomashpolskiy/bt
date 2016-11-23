package bt.module;

import bt.data.DataDescriptorFactory;
import bt.data.IDataDescriptorFactory;
import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.ConnectionHandlerFactory;
import bt.net.IConnectionHandlerFactory;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.net.MessageDispatcher;
import bt.net.PeerConnectionPool;
import bt.protocol.HandshakeFactory;
import bt.protocol.IHandshakeFactory;
import bt.service.AdhocTorrentRegistry;
import bt.service.ClasspathApplicationService;
import bt.service.ConfigurationService;
import bt.service.DefaultIdService;
import bt.service.ExecutorServiceProvider;
import bt.service.IApplicationService;
import bt.service.IConfigurationService;
import bt.service.INetworkService;
import bt.service.IPeerRegistry;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.ITorrentRegistry;
import bt.service.IdService;
import bt.service.NetworkService;
import bt.service.PeerRegistry;
import bt.service.PeerSourceFactory;
import bt.service.RuntimeLifecycleBinder;
import bt.torrent.data.DataWorkerFactory;
import bt.torrent.data.IDataWorkerFactory;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerService;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

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
        return Multibinder.newSetBinder(binder, Object.class, MessagingAgent.class);
    }

    @Override
    public void configure(Binder binder) {

        ServiceModule.contributeMessagingAgent(binder);
        ServiceModule.contributePeerSourceFactory(binder);

        binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
        binder.bind(INetworkService.class).to(NetworkService.class).in(Singleton.class);
        binder.bind(IApplicationService.class).to(ClasspathApplicationService.class).in(Singleton.class);
        binder.bind(IdService.class).to(DefaultIdService.class).in(Singleton.class);
        binder.bind(ITrackerService.class).to(TrackerService.class).in(Singleton.class);
        binder.bind(IConfigurationService.class).to(ConfigurationService.class).in(Singleton.class);
        binder.bind(ITorrentRegistry.class).to(AdhocTorrentRegistry.class).in(Singleton.class);
        binder.bind(IDataDescriptorFactory.class).to(DataDescriptorFactory.class).in(Singleton.class);
        binder.bind(IPeerConnectionPool.class).to(PeerConnectionPool.class).in(Singleton.class);
        binder.bind(IMessageDispatcher.class).to(MessageDispatcher.class).in(Singleton.class);
        binder.bind(IDataWorkerFactory.class).to(DataWorkerFactory.class).in(Singleton.class);
        binder.bind(IHandshakeFactory.class).to(HandshakeFactory.class).in(Singleton.class);
        binder.bind(IConnectionHandlerFactory.class).to(ConnectionHandlerFactory.class).in(Singleton.class);
        binder.bind(IRuntimeLifecycleBinder.class).to(RuntimeLifecycleBinder.class).in(Singleton.class);
        binder.bind(IPeerRegistry.class).to(PeerRegistry.class).in(Singleton.class);

        // TODO: register a shutdown hook in the runtime
        binder.bind(ExecutorService.class).annotatedWith(ClientExecutor.class)
                .toProvider(ExecutorServiceProvider.class).in(Singleton.class);
    }
}
