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
import bt.service.ExecutorServiceProvider;
import bt.service.IApplicationService;
import bt.service.IConfigurationService;
import bt.service.IdService;
import bt.service.INetworkService;
import bt.service.IPeerRegistry;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.ITorrentRegistry;
import bt.service.DefaultIdService;
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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class ServiceModule implements Module {

    private Set<PeerSourceFactory> extraPeerSourceFactories;
    private Set<Class<? extends PeerSourceFactory>> extraPeerSourceFactoryTypes;
    private Set<Object> messagingAgents;
    private Set<Class<?>> messagingAgentTypes;

    public void addPeerSourceFactory(PeerSourceFactory peerSourceFactory) {
        Objects.requireNonNull(peerSourceFactory);
        if (extraPeerSourceFactories == null) {
            extraPeerSourceFactories = new HashSet<>();
        }
        extraPeerSourceFactories.add(peerSourceFactory);
    }

    public void addPeerSourceFactoryType(Class<? extends PeerSourceFactory> peerSourceFactoryType) {
        Objects.requireNonNull(peerSourceFactoryType);
        if (extraPeerSourceFactoryTypes == null) {
            extraPeerSourceFactoryTypes = new HashSet<>();
        }
        extraPeerSourceFactoryTypes.add(peerSourceFactoryType);
    }

    public void addMessagingAgent(Object messagingAgent) {
        Objects.requireNonNull(messagingAgent);
        if (messagingAgents == null) {
            messagingAgents = new HashSet<>();
        }
        messagingAgents.add(messagingAgent);
    }

    public void addMessagingAgentType(Class<?> messagingAgentType) {
        Objects.requireNonNull(messagingAgentType);
        if (messagingAgentTypes == null) {
            messagingAgentTypes = new HashSet<>();
        }
        messagingAgentTypes.add(messagingAgentType);
    }

    @Override
    public void configure(Binder binder) {

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

        binder.bind(ExecutorService.class).annotatedWith(ClientExecutor.class)
                .toProvider(ExecutorServiceProvider.class).in(Singleton.class);

        Multibinder<PeerSourceFactory> peerSources = Multibinder.newSetBinder(binder, PeerSourceFactory.class);
        if (extraPeerSourceFactories != null) {
            extraPeerSourceFactories.forEach(peerSources.addBinding()::toInstance);
        }
        if (extraPeerSourceFactoryTypes != null) {
            extraPeerSourceFactoryTypes.forEach(peerSources.addBinding()::to);
        }

        Multibinder<Object> messagingAgentsBinder = Multibinder.newSetBinder(binder, Object.class, MessagingAgent.class);
        if (messagingAgents != null) {
            messagingAgents.forEach(agent -> messagingAgentsBinder.addBinding().toInstance(agent));
        }
        if (messagingAgentTypes != null) {
            messagingAgentTypes.forEach(agentType -> messagingAgentsBinder.addBinding().to(agentType));
        }
    }
}
