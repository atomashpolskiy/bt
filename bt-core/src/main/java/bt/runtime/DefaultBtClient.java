package bt.runtime;

import bt.BtException;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import bt.torrent.data.IDataWorker;

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
public class DefaultBtClient implements BtClient {

    private ITorrentDescriptor delegate;
    private TorrentSession session;
    private IDataWorker dataWorker;

    private ExecutorService executor;
    private Optional<CompletableFuture<?>> future;
    private Optional<Consumer<TorrentSessionState>> listener;
    private Optional<ScheduledFuture<?>> listenerFuture;

    /**
     * @since 1.0
     */
    public DefaultBtClient(ExecutorService executor, ITorrentDescriptor delegate,
                    TorrentSession session, IDataWorker dataWorker) {

        this.delegate = delegate;
        this.session = session;
        this.dataWorker = dataWorker;
        this.executor = executor;
        this.future = Optional.empty();
        this.listener = Optional.empty();
        this.listenerFuture = Optional.empty();
    }

    @Override
    public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {

        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        this.listener = Optional.of(listener);
        this.listenerFuture = Optional.of(scheduledExecutor.scheduleAtFixedRate(
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
}
