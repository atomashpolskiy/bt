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

package bt;

import bt.runtime.BtClient;
import bt.torrent.TorrentSessionState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client decorator with lazy (on-demand) initialization.
 *
 * @since 1.0
 */
class LazyClient implements BtClient {

    private Supplier<BtClient> clientSupplier;
    private volatile BtClient delegate;

    /**
     * @since 1.0
     */
    public LazyClient(Supplier<BtClient> clientSupplier) {
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
    public boolean isStarted() {
        return delegate != null && delegate.isStarted();
    }
}
