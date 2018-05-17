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

import bt.BtException;
import bt.event.EventSource;
import bt.module.ClientExecutor;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IRuntimeLifecycleBinder.LifecycleEvent;
import bt.service.LifecycleBinding;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A runtime is an orchestrator of multiple simultaneous torrent sessions.
 * It provides a DI container with shared services and manages the application's lifecycle.
 *
 * @since 1.0
 */
public class BtRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtRuntime.class);

    /**
     * @return Runtime builder with default configuration
     * @since 1.0
     */
    public static BtRuntimeBuilder builder() {
        return new BtRuntimeBuilder();
    }

    /**
     * @param config Custom configuration
     * @return Runtime builder
     * @since 1.0
     */
    public static BtRuntimeBuilder builder(Config config) {
        return new BtRuntimeBuilder(config);
    }

    /**
     * Creates a vanilla runtime with default configuration
     *
     * @return Runtime without any extensions
     * @since 1.0
     */
    public static BtRuntime defaultRuntime() {
        return builder().build();
    }

    private Injector injector;
    private Config config;
    private Set<BtClient> knownClients;
    private ExecutorService clientExecutor;
    private AtomicBoolean started;
    private final Object lock;

    private boolean manualShutdownOnly;

    BtRuntime(Injector injector, Config config) {
        Runtime.getRuntime().addShutdownHook(new Thread("bt.runtime.shutdown-manager") {
            @Override
            public void run() {
                shutdown();
            }
        });

        this.injector = injector;
        this.config = config;
        this.knownClients = ConcurrentHashMap.newKeySet();
        this.clientExecutor = injector.getBinding(Key.get(ExecutorService.class, ClientExecutor.class))
                .getProvider().get();
        this.started = new AtomicBoolean(false);
        this.lock = new Object();
    }

    /**
     * Disable automatic runtime shutdown, when all clients have been stopped.
     *
     * @since 1.0
     */
    void disableAutomaticShutdown() {
        this.manualShutdownOnly = true;
    }

    /**
     * @return Injector instance
     * @since 1.0
     */
    public Injector getInjector() {
        return injector;
    }

    /**
     * @return Runtime configuration
     * @since 1.0
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Convenience method to get an instance of a shared DI service.
     *
     * @return Instance of a shared DI service
     * @since 1.0
     */
    public <T> T service(Class<T> serviceType) {
        return injector.getInstance(serviceType);
    }

    /**
     * @return true if this runtime is up and running
     * @since 1.0
     */
    public boolean isRunning() {
        return started.get();
    }

    /**
     * Manually start the runtime (possibly with no clients attached).
     *
     * @since 1.0
     */
    public void startup() {
        if (started.compareAndSet(false, true)) {
            synchronized (lock) {
                runHooks(LifecycleEvent.STARTUP, e -> LOGGER.error("Error on runtime startup", e));
            }
        }
    }

    /**
     * Attach the provided client to this runtime.
     *
     * @since 1.0
     */
    public void attachClient(BtClient client) {
        knownClients.add(client);
    }

    /**
     * Detach the client from this runtime.
     *
     * @since 1.0
     */
    public void detachClient(BtClient client) {
        if (knownClients.remove(client)) {
            if (!manualShutdownOnly && knownClients.isEmpty()) {
                shutdown();
            }
        } else {
            throw new IllegalArgumentException("Unknown client: " + client);
        }
    }

    /**
     * Get all clients, that are attached to this runtime.
     *
     * @since 1.1
     */
    public Collection<BtClient> getClients() {
        return Collections.unmodifiableCollection(knownClients);
    }

    public EventSource getEventSource() {
        return service(EventSource.class);
    }

    /**
     * Manually initiate the runtime shutdown procedure, which includes:
     * - stopping all attached clients
     * - stopping all workers and executors, that were created inside this runtime
     *   and registered via {@link IRuntimeLifecycleBinder}
     *
     * @since 1.0
     */
    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            synchronized (lock) {
                knownClients.forEach(client -> {
                    try {
                        client.stop();
                    } catch (Throwable e) {
                        LOGGER.error("Error when stopping client", e);
                    }
                });

                runHooks(LifecycleEvent.SHUTDOWN, this::onShutdownHookError);
                clientExecutor.shutdownNow();
            }
        }
    }

    private void runHooks(LifecycleEvent event, Consumer<Throwable> errorConsumer) {
        ExecutorService executor = createLifecycleExecutor(event);

        Map<LifecycleBinding, CompletableFuture<Void>> futures = new HashMap<>();
        List<LifecycleBinding> syncBindings = new ArrayList<>();

        service(IRuntimeLifecycleBinder.class).visitBindings(
                event,
                binding -> {
                    if (binding.isAsync()) {
                        futures.put(binding, CompletableFuture.runAsync(toRunnable(event, binding), executor));
                    } else {
                        syncBindings.add(binding);
                    }
                });

        syncBindings.forEach(binding -> {
            String errorMessage = createErrorMessage(event, binding);
            try {
                toRunnable(event, binding).run();
            } catch (Throwable e) {
                errorConsumer.accept(new BtException(errorMessage, e));
            }
        });

        // if the app is shutting down, then we must wait for the futures to complete
        if (event == LifecycleEvent.SHUTDOWN) {
            futures.forEach((binding, future) -> {
                String errorMessage = createErrorMessage(event, binding);
                try {
                    future.get(config.getShutdownHookTimeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    errorConsumer.accept(new BtException(errorMessage, e));
                }
            });
        }

        shutdownGracefully(executor);
    }

    private String createErrorMessage(LifecycleEvent event, LifecycleBinding binding) {
        Optional<String> descriptionOptional = binding.getDescription();
        String errorMessage = "Failed to execute " + event.name().toLowerCase() + " hook: ";
        errorMessage += ": " + (descriptionOptional.orElseGet(() -> binding.getRunnable().toString()));
        return errorMessage;
    }

    private ExecutorService createLifecycleExecutor(LifecycleEvent event) {
        AtomicInteger threadCount = new AtomicInteger();
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "bt.runtime." + event.name().toLowerCase() + "-worker-" + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private void shutdownGracefully(ExecutorService executor) {
        executor.shutdown();
        try {
            long timeout = config.getShutdownHookTimeout().toMillis();
            boolean terminated = executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
            if (!terminated) {
                LOGGER.warn("Failed to shutdown executor in {} millis", timeout);
            }
        } catch (InterruptedException e) {
            // ignore
            LOGGER.warn("Interrupted while waiting for shutdown", e);
            executor.shutdownNow();
        }
    }

    private Runnable toRunnable(LifecycleEvent event, LifecycleBinding binding) {
        return () -> {
            Runnable r = binding.getRunnable();

            Optional<String> descriptionOptional = binding.getDescription();
            String description = descriptionOptional.orElseGet(r::toString);
            LOGGER.debug("Running " + event.name().toLowerCase() + " hook: " + description);

            r.run();
        };
    }

    private void onShutdownHookError(Throwable e) {
        // logging facilities might be unavailable at this moment,
        // so using standard output
        e.printStackTrace(System.err);
        System.err.flush();
    }
}
