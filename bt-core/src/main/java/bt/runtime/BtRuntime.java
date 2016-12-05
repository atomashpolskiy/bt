package bt.runtime;

import bt.module.ClientExecutor;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IRuntimeLifecycleBinder.LifecycleEvent;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        return new BtRuntimeBuilder(new Config());
    }

    /**
     * @param config Custom configuration
     * @return Runtime builder
     */
    public static BtRuntimeBuilder builder(Config config) {
        return new BtRuntimeBuilder(Objects.requireNonNull(config));
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

    private void runHooks(LifecycleEvent event, Consumer<Throwable> errorConsumer) {
        service(IRuntimeLifecycleBinder.class).visitBindings(
                event,
                (description, r) -> {
                    try {
                        if (description.isPresent()) {
                            LOGGER.info("Running " + event.name().toLowerCase() + " hook: " + description.get());
                        }
                        r.run();
                    } catch (Exception e) {
                        errorConsumer.accept(e);
                    }
                });
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

                AtomicInteger threadCount = new AtomicInteger();
                ExecutorService shutdownExecutor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "bt.runtime.shutdown-worker-" + threadCount.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
                service(IRuntimeLifecycleBinder.class).visitBindings(
                    LifecycleEvent.SHUTDOWN,
                    (descriptionOptional, r) -> {
                        Future<Optional<Throwable>> future = shutdownExecutor.submit(toCallable(r));
                        String description = descriptionOptional.orElse(r.toString());
                        try {
                            future.get(config.getShutdownHookTimeout().toMillis(), TimeUnit.MILLISECONDS)
                                    .ifPresent(throwable -> onShutdownHookError(description, throwable));
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            onShutdownHookError(description, e);
                        }
                    });
                shutdownExecutorService(shutdownExecutor);
                shutdownExecutorService(clientExecutor);
            }
        }
    }

    private void shutdownExecutorService(ExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(config.getShutdownHookTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignore
            executor.shutdownNow();
        }
    }

    private Callable<Optional<Throwable>> toCallable(Runnable r) {
        return () -> {
            try {
                r.run();
                return Optional.empty();
            } catch (Throwable e) {
                return Optional.of(e);
            }
        };
    }

    private void onShutdownHookError(String description, Throwable e) {
        System.err.println("Shutdown hook failed: " + description + ". Reason:");
        e.printStackTrace(System.err);
        System.err.flush();
    }
}
