package bt;

import bt.torrent.TorrentSessionState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface BtClient {

    CompletableFuture<?> startAsync();

    CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period);

    void stop();
}
