package bt.service;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface IRuntimeLifecycleBinder {

    enum LifecycleEvent { STARTUP, SHUTDOWN }

    void onStartup(Runnable r);

    void onStartup(String description, Runnable r);

    void onShutdown(Runnable r);

    void onShutdown(String description, Runnable r);

    void visitBindings(LifecycleEvent event, BiConsumer<Optional<String>, Runnable> consumer);
}
