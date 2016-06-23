package bt.it.fixture;

import com.google.inject.Binder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PersonalizedThreadNamesFeature implements BtTestRuntimeFeature {

    @Override
    public void contributeToRuntime(BtTestRuntimeBuilder runtimeBuilder, Binder binder) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 1, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), new ThreadFactory() {

            private AtomicInteger threadId = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,
                        "bt-test-pool-" + threadId.getAndIncrement() +
                                " {peer: " + runtimeBuilder.getAddress() + ":" + runtimeBuilder.getPort() + "}");
            }
        });

        binder.bind(ExecutorService.class).toInstance(executor);
    }
}
