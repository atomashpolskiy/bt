package bt;

import bt.runtime.BtClient;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class DefaultClient implements BtClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClient.class);

    private Runnable processor;
    private TorrentDescriptor delegate;
    private TorrentSession session;
    private Optional<CompletableFuture<?>> future;
    private Optional<Consumer<TorrentSessionState>> listener;
    private Optional<ScheduledFuture<?>> listenerFuture;

    private ExecutorService executor;
    private ScheduledExecutorService listenerExecutor;

    /**
     * @since 1.0
     */
    public DefaultClient(ExecutorService executor, Runnable processor, TorrentDescriptor delegate, TorrentSession session) {
        this.executor = executor;
        this.processor = processor;
        this.delegate = delegate;
        this.session = session;
        this.future = Optional.empty();
        this.listener = Optional.empty();
        this.listenerFuture = Optional.empty();
    }

    @Override
    public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (delegate.isActive()) {
            throw new BtException("Can't start -- already running");
        }

        this.listenerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.listener = Optional.of(listener);
        this.listenerFuture = Optional.of(listenerExecutor.scheduleAtFixedRate(
                () -> listener.accept(session.getState()),
                period, period, TimeUnit.MILLISECONDS));

        return startAsync();
    }

    @Override
    public CompletableFuture<?> startAsync() {
        if (delegate.isActive()) {
            throw new BtException("Can't start -- already running");
        }

        CompletableFuture<?> future = doStart();
        this.future = Optional.of(future);
        return future;
    }

    private CompletableFuture<?> doStart() {
        delegate.start();

        executor.submit(processor);
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            while (delegate.isActive()) {
                try {
                    Thread.sleep(1000);
                    if (session.getState().getPiecesRemaining() == 0) {
                        delegate.complete();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (Throwable e) {
                    LOGGER.warn("Unexpected error", e);
                }
            }
        }, executor);

        future.whenComplete((r, t) -> listener.ifPresent(listener -> listener.accept(session.getState())))
                .whenComplete((r, t) -> listenerFuture.ifPresent(listener -> listener.cancel(true)))
                .whenComplete((r, t) -> listenerExecutor.shutdownNow());

        return future;
    }

    // TODO: as long as this can be used for pausing the client without shutting down the runtime,
    // it would be nice to send CHOKE/NOT_INTERESTED to all connections instead of silently cutting out
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
    public boolean isStarted() {
        return delegate.isActive();
    }
}
