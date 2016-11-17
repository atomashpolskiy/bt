package bt.runtime;

import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client decorator with lazy (on-demand) initialization.
 *
 * @since 1.0
 */
public class LazyBtClient implements BtClient {

    private Supplier<BtClient> clientSupplier;
    private volatile BtClient delegate;

    /**
     * @since 1.0
     */
    public LazyBtClient(Supplier<BtClient> clientSupplier) {
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
