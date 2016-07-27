package bt;

import bt.data.DataAccessFactory;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.net.IConnectionHandlerFactory;
import bt.net.IPeerConnectionPool;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import bt.service.ITorrentRegistry;
import bt.torrent.IDataWorker;
import bt.torrent.IDataWorkerFactory;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.PieceManager;
import bt.torrent.PieceSelector;
import bt.torrent.RarestFirstSelector;
import bt.torrent.TorrentSessionState;
import bt.torrent.TorrentSession;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Bt {

    public static Bt client(BtRuntime runtime) {
        return new Bt(runtime);
    }

    private BtRuntime runtime;

    private URL metainfoUrl;
    private PieceSelector pieceSelector;
    private boolean eagerInit;

    private Bt(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        pieceSelector = RarestFirstSelector.randomized();
    }

    public Bt url(URL metainfoUrl) {
        this.metainfoUrl = Objects.requireNonNull(metainfoUrl);
        return this;
    }

    public Bt selector(PieceSelector pieceSelector) {
        this.pieceSelector = Objects.requireNonNull(pieceSelector);
        return this;
    }

    public Bt eagerInit() {
        eagerInit = true;
        return this;
    }

    public BtClient build(DataAccessFactory dataAccessFactory) {

        Objects.requireNonNull(metainfoUrl, "Missing metainfo file URL");

        BtClient handle;
        if (eagerInit) {
            handle = createClient(dataAccessFactory);
        } else {
            handle = new LazyBtClient(() -> createClient(dataAccessFactory));
        }
        runtime.registerTorrentHandle(handle);
        return handle;
    }

    private BtClient createClient(DataAccessFactory dataAccessFactory) {

        IMetadataService metadataService = runtime.service(IMetadataService.class);
        Torrent torrent = metadataService.fromUrl(metainfoUrl);

        ITorrentRegistry torrentRegistry = runtime.service(ITorrentRegistry.class);
        ITorrentDescriptor descriptor = torrentRegistry.getOrCreateDescriptor(torrent, dataAccessFactory);

        IDataWorkerFactory dataWorkerFactory = runtime.service(IDataWorkerFactory.class);
        IDataWorker dataWorker = dataWorkerFactory.createWorker(descriptor.getDataDescriptor());

        IPeerRegistry peerRegistry = runtime.service(IPeerRegistry.class);
        IPeerConnectionPool connectionPool = runtime.service(IPeerConnectionPool.class);
        IConfigurationService configurationService = runtime.service(IConfigurationService.class);
        IConnectionHandlerFactory connectionHandlerFactory = runtime.service(IConnectionHandlerFactory.class);

        PieceManager pieceManager = new PieceManager(pieceSelector, descriptor.getDataDescriptor().getChunkDescriptors());
        TorrentSession session = new TorrentSession(connectionPool, configurationService,
                connectionHandlerFactory, pieceManager, dataWorker, torrent);

        dataWorker.addVerifiedPieceListener(session::onPieceVerified);
        peerRegistry.addPeerConsumer(torrent, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        ExecutorService executorService = runtime.service(ExecutorService.class);
        return new RuntimeAwareBtClient(runtime, new DefaultBtClient(executorService, descriptor, session, dataWorker));
    }

    private static class LazyBtClient implements BtClient {

        private Supplier<BtClient> clientSupplier;
        private volatile BtClient delegate;

        LazyBtClient(Supplier<BtClient> clientSupplier) {
            this.clientSupplier = clientSupplier;
        }

        private synchronized void initClient() {
            if (delegate == null) {
                delegate = clientSupplier.get();
            }
        }

        @Override
        public CompletableFuture<?> startAsync() {
            if (delegate == null) {
                initClient();
            }
            return delegate.startAsync();
        }

        @Override
        public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
            if (delegate == null) {
                initClient();
            }
            return delegate.startAsync(listener, period);
        }

        @Override
        public void stop() {
            if (delegate == null) {
                initClient();
            }
            delegate.stop();
        }
    }

    private static class RuntimeAwareBtClient implements BtClient {

        private BtRuntime runtime;
        private BtClient delegate;

        RuntimeAwareBtClient(BtRuntime runtime, BtClient delegate) {
            this.runtime = runtime;
            this.delegate = delegate;
        }

        @Override
        public CompletableFuture<?> startAsync() {
            ensureRuntimeStarted();
            return delegate.startAsync();
        }

        @Override
        public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
            ensureRuntimeStarted();
            return delegate.startAsync(listener, period);
        }

        private void ensureRuntimeStarted() {
            if (!runtime.isRunning()) {
                runtime.startup();
            }
        }

        @Override
        public void stop() {
            delegate.stop();
        }
    }

    private static class DefaultBtClient implements BtClient {

        private Executor executor;
        private ITorrentDescriptor delegate;
        private TorrentSession session;
        private IDataWorker dataWorker;

        private Optional<CompletableFuture<?>> future;
        private Optional<Consumer<TorrentSessionState>> listener;
        private Optional<ScheduledFuture<?>> listenerFuture;

        DefaultBtClient(Executor executor, ITorrentDescriptor delegate,
                        TorrentSession session, IDataWorker dataWorker) {

            this.executor = executor;
            this.delegate = delegate;
            this.session = session;
            this.dataWorker = dataWorker;

            future = Optional.empty();
            listener = Optional.empty();
            listenerFuture = Optional.empty();
        }

        @Override
        public void stop() {
            try {
                delegate.stop();
            } finally {
                future.ifPresent(future -> future.complete(null));
            }
        }

        @Override
        public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {

            ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

            this.listener = Optional.of(listener);
            listenerFuture = Optional.of(scheduledExecutor.scheduleAtFixedRate(
                    () -> listener.accept(getState()),
                    period, period, TimeUnit.MILLISECONDS));

            return startAsync();
        }

        @Override
        public CompletableFuture<?> startAsync() {

            if (future.isPresent()) {
                return future.get();
            }

            CompletableFuture<?> future = doStart();
            this.future = Optional.of(future);
            return future;
        }

        private CompletableFuture<?> doStart() {

            if (delegate.isActive()) {
                throw new BtException("Can't start -- already running");
            }

            delegate.start();

            CompletableFuture<?> future = CompletableFuture.runAsync(dataWorker, executor);

            future.thenRun(() -> listener.ifPresent(listener -> listener.accept(getState())))
                    .thenRun(() -> listenerFuture.ifPresent(listener -> listener.cancel(true)));

            return future;
        }

        private TorrentSessionState getState() {
            return session.getState();
        }
    }
}
