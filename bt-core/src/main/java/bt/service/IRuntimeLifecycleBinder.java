package bt.service;

import java.util.function.Consumer;

public interface IRuntimeLifecycleBinder {

    enum LifecycleEvent { STARTUP, SHUTDOWN }

    void onStartup(Runnable r);

    void onShutdown(Runnable r);

    void visitBindings(LifecycleEvent event, Consumer<Runnable> consumer);
}
