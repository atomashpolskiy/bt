package bt;

import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.IPeerConnectionPool;
import bt.net.PeerConnectionPool;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public class BtRuntimeBuilder {

    public static BtRuntimeBuilder builder() {
        return new BtRuntimeBuilder();
    }

    private Class<? extends IShutdownService> shutdownServiceType;
    private ExecutorService executorService;
    private List<BtAdapter> adapters;

    private BtRuntimeBuilder() {
        shutdownServiceType = JVMShutdownService.class;
    }

    public <T extends IShutdownService> BtRuntimeBuilder shutdownService(Class<T> shutdownServiceType) {
        this.shutdownServiceType = Objects.requireNonNull(shutdownServiceType);
        return this;
    }

    public BtRuntimeBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
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

            binder.bind(IShutdownService.class).to(shutdownServiceType).in(Singleton.class);

            if (executorService == null) {
                binder.bind(ExecutorService.class).toProvider(ExecutorServiceProvider.class);
            } else {
                binder.bind(ExecutorService.class).toInstance(executorService);
            }

            if (adapters != null) {
                adapters.forEach(adapter -> adapter.contributeToRuntime(binder));
            }
        };

        return Guice.createInjector(module);
    }
}
