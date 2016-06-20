package bt.torrent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface TorrentHandle extends ITorrentDescriptor {

    CompletableFuture<?> startAsync();
    CompletableFuture<?> startAsync(Consumer<TorrentProcessingState> listener, long period);
}
