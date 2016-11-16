package bt.runtime;

import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RuntimeAwareBtClient implements BtClient {

    private BtRuntime runtime;
    private BtClient delegate;

    public RuntimeAwareBtClient(BtRuntime runtime, BtClient delegate) {
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
