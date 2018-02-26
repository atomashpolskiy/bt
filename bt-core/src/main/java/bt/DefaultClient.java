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

import bt.processor.ProcessingContext;
import bt.processor.Processor;
import bt.processor.listener.ListenerSource;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.TorrentSessionState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Basic interface for interaction with torrent processing.
 *
 * @since 1.0
 */
class DefaultClient<C extends ProcessingContext> implements BtClient {

    private BtRuntime runtime;
    private Processor<C> processor;
    private ListenerSource<C> listenerSource;
    private C context;

    private volatile Optional<CompletableFuture<?>> futureOptional;
    private volatile Optional<Consumer<TorrentSessionState>> listenerOptional;

    private volatile ScheduledExecutorService listenerExecutor;

    public DefaultClient(BtRuntime runtime,
                         Processor<C> processor,
                         C context,
                         ListenerSource<C> listenerSource) {
        this.runtime = runtime;
        this.processor = processor;
        this.context = context;
        this.listenerSource = listenerSource;

        this.futureOptional = Optional.empty();
        this.listenerOptional = Optional.empty();
    }

    @Override
    public synchronized CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (futureOptional.isPresent()) {
            throw new BtException("Can't start -- already running");
        }

        this.listenerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.listenerOptional = Optional.of(listener);

        listenerExecutor.scheduleAtFixedRate(this::notifyListener, period, period, TimeUnit.MILLISECONDS);

        return doStartAsync();
    }

    private void notifyListener() {
        listenerOptional.ifPresent(listener ->
                context.getState().ifPresent(listener::accept));
    }

    private void shutdownListener() {
        listenerExecutor.shutdownNow();
    }

    @Override
    public synchronized CompletableFuture<?> startAsync() {
        if (futureOptional.isPresent()) {
            throw new BtException("Can't start -- already running");
        }

        return doStartAsync();
    }

    private CompletableFuture<?> doStartAsync() {
        ensureRuntimeStarted();
        attachToRuntime();

        CompletableFuture<?> future = processor.process(context, listenerSource);

        future.whenComplete((r, t) -> notifyListener())
                .whenComplete((r, t) -> shutdownListener())
                .whenComplete((r, t) -> stop());

        this.futureOptional = Optional.of(future);

        return future;
    }

    @Override
    public synchronized void stop() {
        // order is important (more precisely, unsetting futureOptional BEFORE completing the future)
        // to prevent attempt to detach the client after it has already been detached once
        // (may happen when #stop() is called from the outside)
        if (futureOptional.isPresent()) {
            CompletableFuture<?> f = futureOptional.get();
            futureOptional = Optional.empty();
            detachFromRuntime();
            f.complete(null);
        }
    }

    private void ensureRuntimeStarted() {
        if (!runtime.isRunning()) {
            runtime.startup();
        }
    }

    private void attachToRuntime() {
        runtime.attachClient(this);
    }

    private void detachFromRuntime() {
        runtime.detachClient(this);
    }

    @Override
    public synchronized boolean isStarted() {
        return futureOptional.isPresent();
    }
}
