package bt.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class RuntimeLifecycleBinder implements IRuntimeLifecycleBinder {

    private Map<LifecycleEvent, List<Binding>> bindings;

    public RuntimeLifecycleBinder() {
        bindings = new HashMap<>();
        for (LifecycleEvent event : LifecycleEvent.values()) {
            bindings.put(event, new ArrayList<>());
        }
    }

    @Override
    public void onStartup(Runnable r) {
        bindings.get(LifecycleEvent.STARTUP).add(new Binding(r));
    }

    @Override
    public void onStartup(String description, Runnable r) {
        bindings.get(LifecycleEvent.STARTUP).add(new Binding(description, r));
    }

    @Override
    public void onShutdown(Runnable r) {
        bindings.get(LifecycleEvent.SHUTDOWN).add(new Binding(r));
    }

    @Override
    public void onShutdown(String description, Runnable r) {
        bindings.get(LifecycleEvent.SHUTDOWN).add(new Binding(description, r));
    }

    @Override
    public void visitBindings(LifecycleEvent event, BiConsumer<Optional<String>, Runnable> consumer) {
        bindings.get(event).forEach(binding -> consumer.accept(binding.getDescription(), binding.getRunnable()));
    }

    private static class Binding {

        private Optional<String> description;
        private Runnable r;

        Binding(Runnable r) {
            this(null, r);
        }

        Binding(String description, Runnable r) {
            this.description = Optional.ofNullable(description);
            this.r = r;
        }

        public Optional<String> getDescription() {
            return description;
        }

        public Runnable getRunnable() {
            return r;
        }
    }
}
