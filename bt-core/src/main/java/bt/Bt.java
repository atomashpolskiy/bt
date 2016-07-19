package bt;

import bt.data.DataAccessFactory;
import bt.data.IDataDescriptor;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.net.IConnectionHandlerFactory;
import bt.net.IPeerConnectionPool;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import bt.service.IShutdownService;
import bt.service.ITorrentRegistry;
import bt.torrent.IDataWorker;
import bt.torrent.IDataWorkerFactory;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.PieceManager;
import bt.torrent.PieceSelector;
import bt.torrent.RarestFirstSelector;
import bt.torrent.TorrentHandle;
import bt.torrent.TorrentProcessingState;
import bt.torrent.TorrentProcessor;

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
            TorrentProcessor processor = new TorrentProcessor(connectionPool, configurationService,
                    connectionHandlerFactory, pieceManager, dataWorker, torrent, descriptor);

            peerRegistry.addPeerConsumer(torrent, processor);
            connectionPool.addConnectionListener(processor);

            IShutdownService shutdownService = runtime.service(IShutdownService.class);
            ExecutorService executorService = runtime.service(ExecutorService.class);
            return new DefaultTorrentHandle(executorService, shutdownService, pieceManager, descriptor, processor, dataWorker);
        }
    }

    private static class DefaultTorrentHandle implements TorrentHandle {

        private Executor executor;
        private IShutdownService shutdownService;
        private PieceManager pieceManager;
        private ITorrentDescriptor delegate;
        private TorrentProcessor processor;
        private IDataWorker dataWorker;

        private Optional<CompletableFuture<?>> future;
        private Optional<Consumer<TorrentProcessingState>> listener;
        private Optional<ScheduledFuture<?>> listenerFuture;

        DefaultTorrentHandle(Executor executor, IShutdownService shutdownService, PieceManager pieceManager,
                             ITorrentDescriptor delegate, TorrentProcessor processor, IDataWorker dataWorker) {

            this.executor = executor;
            this.shutdownService = shutdownService;
            this.pieceManager = pieceManager;
            this.delegate = delegate;
            this.processor = processor;
            this.dataWorker = dataWorker;

            future = Optional.empty();
            listener = Optional.empty();
            listenerFuture = Optional.empty();
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void start() {
            doStart().join();
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
        public void complete() {
            delegate.complete();
        }

        @Override
        public IDataDescriptor getDataDescriptor() {
            return delegate.getDataDescriptor();
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
            CompletableFuture<?> future = doStart();
            this.future = Optional.of(future);
            return future;
        }

        private CompletableFuture<?> doStart() {

            if (isActive()) {
                throw new BtException("Can't start -- already running");
            }

            delegate.start();

            CompletableFuture<?> processorFuture = CompletableFuture.runAsync(processor, executor),
                                 dataWorkerFuture = CompletableFuture.runAsync(dataWorker, executor);

            CompletableFuture<?> future = CompletableFuture.anyOf(processorFuture, dataWorkerFuture);

            future.thenRun(shutdownService::shutdownNow)
                    .thenRun(() -> listener.ifPresent(listener -> listener.accept(getState())))
                    .thenRun(() -> listenerFuture.ifPresent(listener -> listener.cancel(true)));

            return future;
        }

        private TorrentProcessingState getState() {
            return () -> pieceManager.piecesLeft();
        }
    }
}
