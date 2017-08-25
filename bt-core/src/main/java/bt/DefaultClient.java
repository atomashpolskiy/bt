package bt;

import bt.processor.ChainProcessor;
import bt.processor.ProcessingContext;
import bt.processor.ProcessingStage;
import bt.runtime.BtClient;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import bt.torrent.stub.StubSession;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Basic interface for interaction with torrent processing.
 *
 * @since 1.0
 */
class DefaultClient<C extends ProcessingContext> implements BtClient {

    private TorrentRegistry torrentRegistry;

    private ProcessingStage<C> processor;
    private C context;
    private Optional<CompletableFuture<?>> future;
    private Optional<Consumer<TorrentSessionState>> listener;
    private Optional<ScheduledFuture<?>> listenerFuture;

    private ExecutorService executor;
    private ScheduledExecutorService listenerExecutor;

    private volatile boolean started;

    /**
     * @since 1.0
     */
    public DefaultClient(ExecutorService executor,
                         TorrentRegistry torrentRegistry,
                         ProcessingStage<C> processor,
                         C context) {
        this.torrentRegistry = torrentRegistry;
        this.executor = executor;
        this.processor = processor;
        this.context = context;

        this.future = Optional.empty();
        this.listener = Optional.empty();
        this.listenerFuture = Optional.empty();
    }

    @Override
    public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (started) {
            throw new BtException("Can't start -- already running");
        }
        started = true;

        this.listenerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.listener = Optional.of(listener);
        this.listenerFuture = Optional.of(listenerExecutor.scheduleAtFixedRate(
                () -> listener.accept(getSession().getState()),
                period, period, TimeUnit.MILLISECONDS));

        return doStartAsync();
    }

    @Override
    public CompletableFuture<?> startAsync() {
        if (started) {
            throw new BtException("Can't start -- already running");
        }
        started = true;

        return doStartAsync();
    }

    private CompletableFuture<?> doStartAsync() {
        CompletableFuture<?> future = doStart();
        this.future = Optional.of(future);
        return future;
    }

    private CompletableFuture<?> doStart() {
        CompletableFuture<?> future = CompletableFuture.runAsync(
                () -> ChainProcessor.execute(processor, context), executor);

        future.whenComplete((r, t) -> listener.ifPresent(listener -> listener.accept(getSession().getState())))
                .whenComplete((r, t) -> listenerFuture.ifPresent(listener -> listener.cancel(true)))
                .whenComplete((r, t) -> listenerExecutor.shutdownNow());

        return future;
    }

    // TODO: as long as this can be used for pausing the client without shutting down the runtime,
    // it would be nice to send CHOKE/NOT_INTERESTED to all connections instead of silently cutting out
    @Override
    public void stop() {
        try {
            // TODO: should also announce STOP to tracker here
            context.getTorrentId().ifPresent(torrentId -> {
                torrentRegistry.getDescriptor(torrentId).ifPresent(TorrentDescriptor::stop);
            });
        } finally {
            future.ifPresent(future -> future.complete(null));
            started = false;
        }
    }

    @Override
    public TorrentSession getSession() {
        return context.getSession().orElse(StubSession.instance());
    }

    @Override
    public boolean isStarted() {
        return started;
    }
}
