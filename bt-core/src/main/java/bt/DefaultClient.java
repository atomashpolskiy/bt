package bt;

import bt.processor.ProcessingContext;
import bt.processor.ProcessingFuture;
import bt.processor.Processor;
import bt.processor.listener.ListenerSource;
import bt.runtime.BtClient;
import bt.torrent.TorrentSessionState;

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

    private Processor<C> processor;
    private ListenerSource<C> listenerSource;
    private C context;
    private Optional<ProcessingFuture> processingFutureOptional;
    private Optional<CompletableFuture<?>> future;
    private Optional<Consumer<TorrentSessionState>> listener;
    private Optional<ScheduledFuture<?>> listenerFuture;

    private ExecutorService executor;
    private ScheduledExecutorService listenerExecutor;

    private volatile boolean started;

    public DefaultClient(Processor<C> processor,
                         C context,
                         ListenerSource<C> listenerSource,
                         ExecutorService executor) {
        this.processor = processor;
        this.context = context;
        this.listenerSource = listenerSource;
        this.executor = executor;

        this.processingFutureOptional = Optional.empty();
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
                this::notifyListener, period, period, TimeUnit.MILLISECONDS));

        return doStartAsync();
    }

    private void notifyListener() {
        if (listener.isPresent()) {
            Optional<TorrentSessionState> state = context.getState();
            if (state.isPresent()) {
                listener.get().accept(state.get());
            }
        }
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
        this.processingFutureOptional = Optional.of(processor.process(context, listenerSource));
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> processingFutureOptional.get().get(), executor);

        future.whenComplete((r, t) -> notifyListener())
                .whenComplete((r, t) -> listenerFuture.ifPresent(listener -> listener.cancel(true)))
                .whenComplete((r, t) -> listenerExecutor.shutdownNow());

        return future;
    }

    @Override
    public void stop() {
        try {
            processingFutureOptional.ifPresent(ProcessingFuture::cancel);
        } finally {
            future.ifPresent(future -> future.complete(null));
            started = false;
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }
}
