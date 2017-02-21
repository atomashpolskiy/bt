package bt.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class RuntimeLifecycleBinder implements IRuntimeLifecycleBinder {

    private Map<LifecycleEvent, List<LifecycleBinding>> bindings;

    public RuntimeLifecycleBinder() {
        bindings = new HashMap<>();
        for (LifecycleEvent event : LifecycleEvent.values()) {
            bindings.put(event, new ArrayList<>());
        }
    }

    @Override
    public void onStartup(Runnable r) {
        bindings.get(LifecycleEvent.STARTUP).add(LifecycleBinding.bind(r).build());
    }

    @Override
    public void onStartup(String description, Runnable r) {
        bindings.get(LifecycleEvent.STARTUP).add(LifecycleBinding.bind(r).description(description).build());
    }

    @Override
    public void onStartup(LifecycleBinding binding) {
        bindings.get(LifecycleEvent.STARTUP).add(binding);
    }

    @Override
    public void onShutdown(Runnable r) {
        bindings.get(LifecycleEvent.SHUTDOWN).add(LifecycleBinding.bind(r).async().build());
    }

    @Override
    public void onShutdown(String description, Runnable r) {
        bindings.get(LifecycleEvent.SHUTDOWN).add(
                LifecycleBinding.bind(r).description(description).async().build());
    }

    @Override
    public void onShutdown(LifecycleBinding binding) {
        bindings.get(LifecycleEvent.SHUTDOWN).add(binding);
    }

    @Override
    public void addBinding(LifecycleEvent event, LifecycleBinding binding) {
        bindings.get(event).add(binding);
    }

    @Override
    public void visitBindings(LifecycleEvent event, Consumer<LifecycleBinding> consumer) {
        bindings.get(event).forEach(consumer::accept);
    }
}
