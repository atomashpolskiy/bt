package bt;

import bt.data.DataAccessFactory;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.net.IConnectionHandlerFactory;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import bt.service.ITorrentRegistry;
import bt.torrent.DefaultTorrentSession;
import bt.torrent.data.IDataWorker;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.PieceManager;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.RarestFirstSelectionStrategy;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import bt.torrent.messaging.GenericConsumer;
import bt.torrent.messaging.MessageConsumer;
import bt.torrent.messaging.MessageProducer;
import bt.torrent.messaging.RequestProducer;
import bt.torrent.messaging.PieceProducer;

import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    private PieceSelectionStrategy pieceSelectionStrategy;
    private boolean eagerInit;

    private Bt(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        pieceSelectionStrategy = RarestFirstSelectionStrategy.randomized();
    }

    public Bt url(URL metainfoUrl) {
        this.metainfoUrl = Objects.requireNonNull(metainfoUrl);
        return this;
    }

    public Bt selector(PieceSelectionStrategy pieceSelectionStrategy) {
        this.pieceSelectionStrategy = Objects.requireNonNull(pieceSelectionStrategy);
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
        IMessageDispatcher messageDispatcher = runtime.service(IMessageDispatcher.class);

        PieceManager pieceManager = new PieceManager(descriptor.getDataDescriptor().getBitfield(), pieceSelectionStrategy);

        Set<MessageConsumer> messageConsumers = new HashSet<>();
        messageConsumers.add(new GenericConsumer(pieceManager, dataWorker));

        Set<MessageProducer> messageProducers = new HashSet<>();
        messageProducers.add(new PieceProducer(dataWorker));
        messageProducers.add(new RequestProducer(descriptor.getDataDescriptor().getChunkDescriptors(), pieceManager));

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, configurationService,
                connectionHandlerFactory, pieceManager, messageDispatcher, messageConsumers, messageProducers, torrent);

        dataWorker.addVerifiedPieceListener(session::onPieceVerified);
        peerRegistry.addPeerConsumer(torrent, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        return new RuntimeAwareBtClient(runtime,
                new DefaultBtClient(runtime.getClientExecutor(), descriptor, session, dataWorker));
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
                return;
            }
            delegate.stop();
        }

        @Override
        public TorrentSession getSession() {
            if (delegate == null) {
                initClient();
            }
            return delegate.getSession();
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

        @Override
        public TorrentSession getSession() {
            return delegate.getSession();
        }
    }

    private static class DefaultBtClient implements BtClient {

        private ITorrentDescriptor delegate;
        private TorrentSession session;
        private IDataWorker dataWorker;

        private ExecutorService executor;
        private Optional<CompletableFuture<?>> future;
        private Optional<Consumer<TorrentSessionState>> listener;
        private Optional<ScheduledFuture<?>> listenerFuture;

        DefaultBtClient(ExecutorService executor, ITorrentDescriptor delegate,
                        TorrentSession session, IDataWorker dataWorker) {
            this.delegate = delegate;
            this.session = session;
            this.dataWorker = dataWorker;

            this.executor = executor;
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
        public TorrentSession getSession() {
            return session;
        }

        @Override
        public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {

            ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

            this.listener = Optional.of(listener);
            listenerFuture = Optional.of(scheduledExecutor.scheduleAtFixedRate(
                    () -> listener.accept(session.getState()),
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

            future.thenRun(() -> listener.ifPresent(listener -> listener.accept(session.getState())))
                    .thenRun(() -> listenerFuture.ifPresent(listener -> listener.cancel(true)));

            return future;
        }
    }
}
