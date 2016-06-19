package bt.service;

import java.io.Closeable;
import java.io.IOException;
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
import java.util.stream.Collectors;

public class JVMShutdownService implements IShutdownService {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(1);

    private Set<Closeable> hooks;

    public JVMShutdownService() {
        hooks = ConcurrentHashMap.newKeySet();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }

    @Override
    public void addShutdownHook(Closeable closeable) {
        hooks.add(closeable);
    }

    private void shutdown() {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        hooks.stream().map(this::shutdownOne).collect(Collectors.toList())
                    .forEach(it -> awaitShutdown(executor.submit(it)));

        executor.shutdownNow();
    }

    private Callable<Optional<Throwable>> shutdownOne(Closeable closeable) {
        return () -> {
            try {
                closeable.close();
                return Optional.empty();
            } catch (IOException e) {
                return Optional.of(e);
            }
        };
    }

    private void awaitShutdown(Future<Optional<Throwable>> future) {
        try {
            future.get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .ifPresent(this::logError);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logError(e);
        }
    }

    private void logError(Throwable e) {
        e.printStackTrace(System.err);
        System.err.flush();
    }
}
