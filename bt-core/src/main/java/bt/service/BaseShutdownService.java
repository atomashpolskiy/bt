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

public abstract class BaseShutdownService implements IShutdownService {

    private Duration shutdownTimeout;
    private Set<Closeable> hooks;

    protected BaseShutdownService(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
        hooks = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void addShutdownHook(Closeable closeable) {
        hooks.add(closeable);
    }

    protected void shutdown() {

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
            future.get(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .ifPresent(this::onError);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            onError(e);
        }
    }

    protected abstract void onError(Throwable e);
}
