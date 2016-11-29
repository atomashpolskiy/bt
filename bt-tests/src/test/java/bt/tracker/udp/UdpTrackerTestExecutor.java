package bt.tracker.udp;

import bt.tracker.TrackerResponse;
import org.junit.rules.ExternalResource;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UdpTrackerTestExecutor extends ExternalResource {

    private final ExecutorService executor;
    private final SingleClientUdpTracker tracker;
    private volatile boolean ignoreErrors;

    public UdpTrackerTestExecutor(SingleClientUdpTracker tracker) {
        this.executor = Executors.newFixedThreadPool(2);
        this.tracker = tracker;
    }

    @Override
    protected void after() {
        executor.shutdownNow();
    }

    private void ignoreInternalErrors() {
        this.ignoreErrors = true;
    }

    void execute(Supplier<TrackerResponse> responseSupplier, Consumer<TrackerResponse> responseConsumer) {

        Set<Throwable> errors = ConcurrentHashMap.newKeySet();

        Object mutex = new Object();
        Consumer<Throwable> errorHandler = error -> {
            synchronized (mutex) {
                if (error != null && !ignoreErrors) {
                    errors.add(error);
                }
            }
        };

        CompletableFuture.anyOf(
                CompletableFuture.runAsync(tracker, executor)
                        .whenComplete((ignore, error) -> errorHandler.accept(error)),
                CompletableFuture.supplyAsync(responseSupplier, executor)
                        .whenComplete((response, error) -> {
                            synchronized (mutex) {
                                if (response != null) {
                                    ignoreInternalErrors();
                                    responseConsumer.accept(response);
                                } else if (error != null) {
                                    errorHandler.accept(error);
                                } else {
                                    throw new NullPointerException("Null response");
                                }
                            }
                        })
        ).join();

        if (!errors.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(out);
            errors.forEach(error -> {
                error.printStackTrace(writer);
                writer.println();
            });
            writer.flush();

            throw new RuntimeException("Unexpected errors:\n\n" + out.toString());
        }
    }
}
