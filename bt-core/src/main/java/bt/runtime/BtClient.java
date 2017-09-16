/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.runtime;

import bt.torrent.TorrentSessionState;

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
     * Check if this client is started.
     *
     * @return true if this client is started
     * @since 1.1
     */
    boolean isStarted();
}
