package bt.data;

import bt.metainfo.TorrentFile;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A class that keeps track of how many more chunks must complete before this file finishes.
 */
public class CompletableTorrentFile {
    private final TorrentFile torrentFile;
    private final AtomicLong countdown;
    private final Consumer<TorrentFile> callback;

    public CompletableTorrentFile(TorrentFile tf, long numPieces, Consumer<TorrentFile> callback) {
        this.torrentFile = Objects.requireNonNull(tf);
        if (numPieces < 0) {
            throw new IllegalArgumentException();
        }
        this.countdown = new AtomicLong(numPieces);
        this.callback = callback;
    }

    /**
     * Countdown the number of chunks needed by 1. Returns true if this chunk has finished
     *
     * @return true iff this chunk is finished
     */
    public boolean countdown() {
        long newCountNeeded = countdown.decrementAndGet();

        if (newCountNeeded < 0)
            throw new IllegalStateException();

        return newCountNeeded == 0;
    }

    public TorrentFile getTorrentFile() {
        return torrentFile;
    }
}
