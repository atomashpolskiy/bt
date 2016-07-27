package bt;

import bt.service.IRuntimeLifecycleBinder;
import bt.service.IRuntimeLifecycleBinder.LifecycleEvent;
import bt.service.IShutdownService;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class BtRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtRuntime.class);

    private Injector injector;
    private Set<BtClient> knownHandles;
    private AtomicBoolean started;
    private final Object lock;

    BtRuntime(Injector injector) {
        this.injector = injector;
        knownHandles = ConcurrentHashMap.newKeySet();
        started = new AtomicBoolean(false);
        lock = new Object();
    }

    public <T> T service(Class<T> serviceType) {
        return injector.getInstance(serviceType);
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

    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            synchronized (lock) {
                knownHandles.forEach(BtClient::stop);
                runHooks(LifecycleEvent.SHUTDOWN, e -> LOGGER.error("Error on runtime shutdown", e));
                service(IShutdownService.class).shutdownNow();
            }
        }
    }

    private void runHooks(LifecycleEvent event, Consumer<Throwable> errorConsumer) {
        service(IRuntimeLifecycleBinder.class).visitBindings(
                event,
                r -> {
                    try {
                        r.run();
                    } catch (Exception e) {
                        errorConsumer.accept(e);
                    }
                });
    }

    void registerTorrentHandle(BtClient handle) {
        knownHandles.add(handle);
    }
}
