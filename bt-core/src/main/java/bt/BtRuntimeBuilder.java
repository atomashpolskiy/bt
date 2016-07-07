package bt;

import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.ExtendedHandshakeHandler;
import bt.net.HandshakeHandler;
import bt.net.HandshakeHandlerFactory;
import bt.net.IHandshakeHandlerFactory;
import bt.net.IPeerConnectionPool;
import bt.net.PeerConnectionPool;
import bt.protocol.HandshakeFactory;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Protocol;
import bt.protocol.ProtocolChain;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.ext.AlphaSortedMessageTypeMapping;
import bt.protocol.ext.ExtendedHandshake;
import bt.protocol.ext.ExtendedHandshakeProvider;
import bt.protocol.ext.ExtendedMessageHandler;
import bt.protocol.ext.ExtendedMessageTypeMapping;
import bt.protocol.ext.ExtendedProtocol;
import bt.service.AdhocTorrentRegistry;
import bt.service.ConfigurationService;
import bt.service.ExecutorServiceProvider;
import bt.service.IConfigurationService;
import bt.service.IIdService;
import bt.service.INetworkService;
import bt.service.IPeerRegistry;
import bt.service.IShutdownService;
import bt.service.ITorrentRegistry;
import bt.service.IdService;
import bt.service.JVMShutdownService;
import bt.service.NetworkService;
import bt.service.PeerRegistry;
import bt.torrent.DataWorkerFactory;
import bt.torrent.IDataWorkerFactory;
import bt.tracker.ITrackerService;
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

    private Class<? extends IShutdownService> shutdownServiceType;
    private ExecutorService executorService;
    private Map<String, ExtendedMessageHandler<?>> extendedMessageHandlers;
    private List<BtAdapter> adapters;

    private BtRuntimeBuilder() {
        shutdownServiceType = JVMShutdownService.class;
    }

    public <T extends IShutdownService> BtRuntimeBuilder shutdownService(Class<T> shutdownServiceType) {
        this.shutdownServiceType = Objects.requireNonNull(shutdownServiceType);
        return this;
    }

    public BtRuntimeBuilder executorService(ExecutorService executorService) {
        this.executorService = Objects.requireNonNull(executorService);
        return this;
    }

    public BtRuntimeBuilder extendedMessageHandler(String messageTypeName, ExtendedMessageHandler<?> handler) {

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
            binder.bind(IPeerRegistry.class).to(PeerRegistry.class).in(Singleton.class);
            binder.bind(ITorrentRegistry.class).to(AdhocTorrentRegistry.class).in(Singleton.class);
            binder.bind(IPeerConnectionPool.class).to(PeerConnectionPool.class).in(Singleton.class);
            binder.bind(IDataWorkerFactory.class).to(DataWorkerFactory.class).in(Singleton.class);
            binder.bind(Protocol.class).to(ProtocolChain.class).in(Singleton.class);
            binder.bind(IHandshakeFactory.class).to(HandshakeFactory.class).in(Singleton.class);
            binder.bind(IHandshakeHandlerFactory.class).to(HandshakeHandlerFactory.class).in(Singleton.class);

            binder.bind(IShutdownService.class).to(shutdownServiceType).in(Singleton.class);

            if (executorService == null) {
                binder.bind(ExecutorService.class).toProvider(ExecutorServiceProvider.class);
            } else {
                binder.bind(ExecutorService.class).toInstance(executorService);
            }

            Multibinder<Protocol> protocols = Multibinder.newSetBinder(binder, Protocol.class);
            protocols.addBinding().to(StandardBittorrentProtocol.class).in(Singleton.class);

            Multibinder<HandshakeHandler> extraHandshakeHandlers = Multibinder.newSetBinder(binder, HandshakeHandler.class);

            if (extendedMessageHandlers != null && extendedMessageHandlers.size() > 0) {

                // create a separate module (for cleaner code)
                binder.bind(ExtendedMessageTypeMapping.class).to(AlphaSortedMessageTypeMapping.class).in(Singleton.class);

                protocols.addBinding().to(ExtendedProtocol.class);
                binder.bind(ExtendedHandshake.class).toProvider(ExtendedHandshakeProvider.class).in(Singleton.class);

                MapBinder<String, ExtendedMessageHandler<?>> extendedMessageHandlerMapBinder =
                        MapBinder.newMapBinder(binder, new TypeLiteral<String>(){}, new TypeLiteral<ExtendedMessageHandler<?>>(){});
                extendedMessageHandlers.forEach((key, value) ->
                        extendedMessageHandlerMapBinder.addBinding(key).toInstance(value));

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
