package bt.service;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorServiceProvider implements Provider<ExecutorService> {

    private IShutdownService shutdownService;
    private ExecutorService executorService;
    private final Object lock;

    @Inject
    public ExecutorServiceProvider(IShutdownService shutdownService) {
        this.shutdownService = shutdownService;
        lock = new Object();
    }

    @Override
    public ExecutorService get() {

        if (executorService == null) {
            synchronized (lock) {
                if (executorService == null) {
                    executorService = Executors.newCachedThreadPool(new ThreadFactory() {

                        private AtomicInteger threadId = new AtomicInteger(1);

                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, "bt-pool-thread-" + threadId.getAndIncrement());
                        }
                    });
                    shutdownService.addShutdownHook(executorService::shutdownNow);
                }
            }
        }

        return executorService;
    }
}
