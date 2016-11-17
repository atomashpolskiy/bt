package bt.runtime;

import bt.service.IRuntimeLifecycleBinder;
import bt.service.IRuntimeLifecycleBinder.LifecycleEvent;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
     * @return Runtime builder
     * @since 1.0
     */
    public static BtRuntimeBuilder builder() {
        return new BtRuntimeBuilder();
    }

    /**
     * Creates a runtime with basic configuration.
     *
     * @return Basic runtime without any extensions
     * @since 1.0
     */
    public static BtRuntime defaultRuntime() {
        return builder().build();
    }

    private Duration shutdownTimeout;

    private Injector injector;
    private Set<BtClient> knownClients;
    private AtomicBoolean started;
    private final Object lock;

    BtRuntime(Injector injector) {
        shutdownTimeout = Duration.ofSeconds(5);
        Runtime.getRuntime().addShutdownHook(new Thread("BtShutdownThread") {
            @Override
            public void run() {
                shutdown();
            }
        });

        this.injector = injector;
        this.knownClients = ConcurrentHashMap.newKeySet();
        this.started = new AtomicBoolean(false);
        this.lock = new Object();
    }

    /**
     * @return Injector instance
     * @since 1.0
     */
    public Injector getInjector() {
        return injector;
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

    void registerClient(BtClient client) {
        knownClients.add(client);
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
                    Thread t = new Thread(r, "BtShutdownHandler-" + threadCount.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
                service(IRuntimeLifecycleBinder.class).visitBindings(
                    LifecycleEvent.SHUTDOWN,
                    (descriptionOptional, r) -> {
                        Future<Optional<Throwable>> future = shutdownExecutor.submit(toCallable(r));
                        String description = descriptionOptional.orElse(r.toString());
                        try {
                            future.get(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
                                    .ifPresent(throwable -> onShutdownHookError(description, throwable));
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            onShutdownHookError(description, e);
                        }
                    });
                shutdownExecutor.shutdown();
                try {
                    shutdownExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ignore
                    shutdownExecutor.shutdownNow();
                }
                // TODO: replace this with @ClientExecutor shutdown call
                // clientExecutor.shutdown();
            }
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
