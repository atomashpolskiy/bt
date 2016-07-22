package bt.torrent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface TorrentHandle {

    CompletableFuture<?> startAsync();

    CompletableFuture<?> startAsync(Consumer<TorrentProcessingState> listener, long period);

    void stop();
}
