package bt.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RuntimeLifecycleBinder implements IRuntimeLifecycleBinder {

    private Map<LifecycleEvent, List<Runnable>> bindings;

    public RuntimeLifecycleBinder() {
        bindings = new HashMap<>();
        for (LifecycleEvent event : LifecycleEvent.values()) {
            bindings.put(event, new ArrayList<>());
        }
    }

    @Override
    public void onStartup(Runnable r) {
        bindings.get(LifecycleEvent.STARTUP).add(r);
    }

    @Override
    public void onShutdown(Runnable r) {
        bindings.get(LifecycleEvent.SHUTDOWN).add(r);
    }

    @Override
    public void visitBindings(LifecycleEvent event, Consumer<Runnable> consumer) {
        bindings.get(event).forEach(consumer::accept);
    }
}
