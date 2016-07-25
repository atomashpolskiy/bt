package bt;

import bt.data.DataDescriptorFactory;
import bt.data.IDataDescriptorFactory;
import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.ConnectionHandler;
import bt.net.ConnectionHandlerFactory;
import bt.net.HandshakeHandler;
import bt.net.IConnectionHandlerFactory;
import bt.net.IPeerConnectionPool;
import bt.net.PeerConnectionPool;
import bt.protocol.HandshakeFactory;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import bt.protocol.StandardBittorrentProtocol;
import bt.runtime.PeerExchangeAdapter;
import bt.runtime.net.ext.ExtendedConnectionHandler;
import bt.runtime.net.ext.ExtendedHandshakeHandler;
import bt.runtime.protocol.ext.AlphaSortedMessageTypeMapping;
import bt.runtime.protocol.ext.ExtendedHandshake;
import bt.runtime.protocol.ext.ExtendedHandshakeProvider;
import bt.runtime.protocol.ext.ExtendedMessageTypeMapping;
import bt.runtime.protocol.ext.ExtendedProtocol;
import bt.runtime.protocol.ext.pex.PeerExchangeMessageHandler;
import bt.service.AdhocTorrentRegistry;
import bt.service.ConfigurationService;
import bt.service.ExecutorServiceProvider;
import bt.service.IConfigurationService;
import bt.service.IIdService;
import bt.service.INetworkService;
import bt.service.IPeerRegistry;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IShutdownService;
import bt.service.ITorrentRegistry;
import bt.service.IdService;
import bt.service.JVMShutdownService;
import bt.service.NetworkService;
import bt.service.PeerRegistry;
import bt.service.PeerSourceFactory;
import bt.service.RuntimeLifecycleBinder;
import bt.torrent.DataWorkerFactory;
import bt.torrent.IDataWorkerFactory;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerPeerSourceFactory;
import bt.tracker.TrackerService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public class BtRuntimeBuilder {

    public static BtRuntimeBuilder builder() {
        return new BtRuntimeBuilder();
    }

    private Map<String, MessageHandler<?>> extendedMessageHandlers;
    private List<BtAdapter> adapters;

    private BtRuntimeBuilder() {

        // default extended protocol handlers
        extendedMessageHandler("ut_pex",  new PeerExchangeMessageHandler());

        // default adapters
        adapter(new PeerExchangeAdapter());
    }

    public BtRuntimeBuilder extendedMessageHandler(String messageTypeName, MessageHandler<?> handler) {

        Objects.requireNonNull(messageTypeName);
        Objects.requireNonNull(handler);

        if (extendedMessageHandlers == null) {
            extendedMessageHandlers = new HashMap<>();
        }
        extendedMessageHandlers.put(messageTypeName, handler);
        return this;
    }

    public BtRuntimeBuilder adapter(BtAdapter adapter) {

        Objects.requireNonNull(adapter);

        if (adapters == null) {
            adapters = new ArrayList<>();
        }
        adapters.add(adapter);
        return this;
    }

    public BtRuntime build() {
        Injector injector = createInjector();
        return new BtRuntime(injector);
    }

    private Injector createInjector() {

        Module module = binder -> {

            binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
            binder.bind(INetworkService.class).to(NetworkService.class).in(Singleton.class);
            binder.bind(IIdService.class).to(IdService.class).in(Singleton.class);
            binder.bind(ITrackerService.class).to(TrackerService.class).in(Singleton.class);
            binder.bind(IConfigurationService.class).to(ConfigurationService.class).in(Singleton.class);
            binder.bind(ITorrentRegistry.class).to(AdhocTorrentRegistry.class).in(Singleton.class);
            binder.bind(IDataDescriptorFactory.class).to(DataDescriptorFactory.class).in(Singleton.class);
            binder.bind(IPeerConnectionPool.class).to(PeerConnectionPool.class).in(Singleton.class);
            binder.bind(IDataWorkerFactory.class).to(DataWorkerFactory.class).in(Singleton.class);
            binder.bind(IHandshakeFactory.class).to(HandshakeFactory.class).in(Singleton.class);
            binder.bind(IConnectionHandlerFactory.class).to(ConnectionHandlerFactory.class).in(Singleton.class);
            binder.bind(ExecutorService.class).toProvider(ExecutorServiceProvider.class);
            binder.bind(IShutdownService.class).to(JVMShutdownService.class).in(Singleton.class);
            binder.bind(IRuntimeLifecycleBinder.class).to(RuntimeLifecycleBinder.class).in(Singleton.class);

            binder.bind(IPeerRegistry.class).to(PeerRegistry.class).in(Singleton.class);
            Multibinder<PeerSourceFactory> peerSources = Multibinder.newSetBinder(binder, PeerSourceFactory.class);
            peerSources.addBinding().to(TrackerPeerSourceFactory.class);

            binder.bind(new TypeLiteral<MessageHandler<Message>>(){})
                    .to(StandardBittorrentProtocol.class).in(Singleton.class);

            MapBinder<Integer, MessageHandler<?>> extraMessageHandlerMapBinder =
                        MapBinder.newMapBinder(binder, new TypeLiteral<Integer>(){}, new TypeLiteral<MessageHandler<?>>(){});
            MapBinder<String, MessageHandler<?>> extendedMessageHandlerMapBinder =
                        MapBinder.newMapBinder(binder, new TypeLiteral<String>(){}, new TypeLiteral<MessageHandler<?>>(){});
            Multibinder<ConnectionHandler> extraConnectionHandlers = Multibinder.newSetBinder(binder, ConnectionHandler.class);
            Multibinder<HandshakeHandler> extraHandshakeHandlers = Multibinder.newSetBinder(binder, HandshakeHandler.class);

            if (extendedMessageHandlers != null && extendedMessageHandlers.size() > 0) {

                // create a separate module (for cleaner code)
                binder.bind(ExtendedMessageTypeMapping.class).to(AlphaSortedMessageTypeMapping.class).in(Singleton.class);
                extraMessageHandlerMapBinder.addBinding(ExtendedProtocol.EXTENDED_MESSAGE_ID).to(ExtendedProtocol.class);

                binder.bind(ExtendedHandshake.class).toProvider(ExtendedHandshakeProvider.class).in(Singleton.class);

                extendedMessageHandlers.forEach((key, value) ->
                        extendedMessageHandlerMapBinder.addBinding(key).toInstance(value));

                extraConnectionHandlers.addBinding().to(ExtendedConnectionHandler.class);
                extraHandshakeHandlers.addBinding().to(ExtendedHandshakeHandler.class);
            }
        };

        if (adapters != null) {
            module = Modules.override(module).with(binder -> {
                adapters.forEach(adapter -> adapter.contributeToRuntime(binder));
            });
        }

        Injector injector = Guice.createInjector(module);

        IShutdownService shutdownService = injector.getInstance(IShutdownService.class);
        ExecutorService executor = injector.getInstance(ExecutorService.class);
        shutdownService.addShutdownHook(executor::shutdownNow);

        return injector;
    }
}
