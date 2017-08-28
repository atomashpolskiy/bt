package bt.runtime;

import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Main API for interaction with a torrent processing worker.
 *
 * @since 1.0
 */
public interface BtClient {

    /**
     * Start torrent processing asynchronously in a separate thread.
     *
     * @return Future, that can be joined by the calling thread
     *         or used in any other way, which is convenient for the caller.
     * @since 1.0
     */
    CompletableFuture<?> startAsync();

    /**
     * Start torrent processing asynchronously in a separate thread
     * and schedule periodic callback invocations.
     *
     * @param listener Callback, that is periodically provided
     *                 with an up-to-date state of torrent session.
     * @param period Interval at which the listener should be invoked, in milliseconds.
     * @return Future, that can be joined by the calling thread
     *         or used in any other way, which is convenient for the caller.
     * @since 1.0
     */
    CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period);

    /**
     * Stop torrent processing.
     *
     * @since 1.0
     */
    void stop();

    /**
     * Provides access to a torrent session.
     * This method can be considered an alternative way to get current state of torrent processing,
     * as well as some other additional information.
     *
     * @return Torrent session, or {@link Optional#empty()}, if the session has not been created yet
     * @since 1.5
     */
    Optional<TorrentSession> getSession();

    /**
     * Check if this client is started.
     *
     * @return true if this client is started
     * @since 1.1
     */
    boolean isStarted();
}
