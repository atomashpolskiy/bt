package bt;

import bt.processor.DefaultProcessingFuture;
import bt.processor.ProcessingContext;
import bt.processor.ProcessingFuture;
import bt.processor.Processor;
import bt.processor.listener.ListenerSource;
import bt.runtime.BtClient;
import bt.torrent.TorrentSessionState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    private volatile Optional<ProcessingFuture> futureOptional;
    private volatile Optional<Consumer<TorrentSessionState>> listenerOptional;

    private volatile ScheduledExecutorService listenerExecutor;

    public DefaultClient(Processor<C> processor,
                         C context,
                         ListenerSource<C> listenerSource) {
        this.processor = processor;
        this.context = context;
        this.listenerSource = listenerSource;

        this.futureOptional = Optional.empty();
        this.listenerOptional = Optional.empty();
    }

    @Override
    public synchronized CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (futureOptional.isPresent()) {
            throw new BtException("Can't start -- already running");
        }

        this.listenerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.listenerOptional = Optional.of(listener);

        listenerExecutor.scheduleAtFixedRate(this::notifyListener, period, period, TimeUnit.MILLISECONDS);

        return doStartAsync();
    }

    private void notifyListener() {
        listenerOptional.ifPresent(listener ->
                context.getState().ifPresent(listener::accept));
    }

    private void shutdownListener() {
        listenerExecutor.shutdownNow();
    }

    @Override
    public synchronized CompletableFuture<?> startAsync() {
        if (futureOptional.isPresent()) {
            throw new BtException("Can't start -- already running");
        }

        return doStartAsync();
    }

    private CompletableFuture<?> doStartAsync() {
        // TODO: this is a workaround for preserving the current public API with CompletableFuture
        // we don't want the CompletableFuture to be present in ProcessingFuture interface,
        // so we have to resort to a cast to get access to the actual future
        DefaultProcessingFuture processingFuture = (DefaultProcessingFuture) processor.process(context, listenerSource);

        processingFuture.getDelegate()
                .whenComplete((r, t) -> notifyListener())
                .whenComplete((r, t) -> shutdownListener());

        this.futureOptional = Optional.of(processingFuture);

        return processingFuture.getDelegate();
    }

    @Override
    public synchronized void stop() {
        if (futureOptional.isPresent()) {
            futureOptional.get().cancel();
            futureOptional = Optional.empty();
        }
    }

    @Override
    public synchronized boolean isStarted() {
        return futureOptional.isPresent();
    }
}
