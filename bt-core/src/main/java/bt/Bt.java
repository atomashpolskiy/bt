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
import bt.torrent.TorrentHandle;
import bt.torrent.TorrentProcessingState;
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

public class Bt {

    public static Builder torrentWorker(BtRuntime runtime) {
        return new Builder(runtime);
    }

    public static class Builder {

        private BtRuntime runtime;

        private URL metainfoUrl;
        private PieceSelector pieceSelector;

        private Builder(BtRuntime runtime) {
            this.runtime = Objects.requireNonNull(runtime);
        }

        public Builder metainfoUrl(URL metainfoUrl) {
            this.metainfoUrl = Objects.requireNonNull(metainfoUrl);
            return this;
        }

        public Builder pieceSelector(PieceSelector pieceSelector) {
            this.pieceSelector = Objects.requireNonNull(pieceSelector);
            return this;
        }

        public TorrentHandle build(DataAccessFactory dataAccessFactory) {

            if (pieceSelector == null) {
                pieceSelector = RarestFirstSelector.randomized();
            }

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

            TorrentHandle handle = new DefaultTorrentHandle(executorService, descriptor, session, dataWorker) {
                @Override
                public CompletableFuture<?> startAsync() {
                    ensureRuntimeStarted();
                    return super.startAsync();
                }

                @Override
                public CompletableFuture<?> startAsync(Consumer<TorrentProcessingState> listener, long period) {
                    ensureRuntimeStarted();
                    return super.startAsync(listener, period);
                }

                private void ensureRuntimeStarted() {
                    if (!runtime.isRunning()) {
                        runtime.startup();
                    }
                }
            };
            runtime.registerTorrentHandle(handle);
            return handle;
        }
    }

    private static class DefaultTorrentHandle implements TorrentHandle {

        private Executor executor;
        private ITorrentDescriptor delegate;
        private TorrentSession session;
        private IDataWorker dataWorker;

        private Optional<CompletableFuture<?>> future;
        private Optional<Consumer<TorrentProcessingState>> listener;
        private Optional<ScheduledFuture<?>> listenerFuture;

        DefaultTorrentHandle(Executor executor, ITorrentDescriptor delegate,
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
        public CompletableFuture<?> startAsync(Consumer<TorrentProcessingState> listener, long period) {

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

        private TorrentProcessingState getState() {
            return session.getState();
        }
    }
}
