package bt;

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

public class BtRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtRuntime.class);

    private Duration shutdownTimeout;

    private Injector injector;
    private Set<BtClient> knownHandles;
    private AtomicBoolean started;
    private final Object lock;

    private ExecutorService clientExecutor;

    BtRuntime(Injector injector) {

        shutdownTimeout = Duration.ofSeconds(5);
        Runtime.getRuntime().addShutdownHook(new Thread("BtShutdownThread") {
            @Override
            public void run() {
                shutdown();
            }
        });

        this.injector = injector;
        knownHandles = ConcurrentHashMap.newKeySet();
        started = new AtomicBoolean(false);
        lock = new Object();

        AtomicInteger threadCount = new AtomicInteger();
        clientExecutor = Executors.newCachedThreadPool(r ->
                new Thread(r, "BtRuntimeThreadPool-Client#" + threadCount.incrementAndGet()));
    }

    public <T> T service(Class<T> serviceType) {
        return injector.getInstance(serviceType);
    }

    void registerTorrentHandle(BtClient handle) {
        knownHandles.add(handle);
    }

    ExecutorService getClientExecutor() {
        return clientExecutor;
    }

    public boolean isRunning() {
        return started.get();
    }

    public void startup() {
        if (started.compareAndSet(false, true)) {
            synchronized (lock) {
                runHooks(LifecycleEvent.STARTUP, e -> LOGGER.error("Error on runtime startup", e));
            }
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

    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            synchronized (lock) {
                knownHandles.forEach(client -> {
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
                clientExecutor.shutdown();
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
