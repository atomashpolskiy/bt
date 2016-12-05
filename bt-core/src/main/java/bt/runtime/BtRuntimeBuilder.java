package bt.runtime;

import bt.BtException;
import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime builder.
 *
 * @since 1.0
 */
public class BtRuntimeBuilder {

    private Config config;
    private Map<Class<? extends Module>, Module> modules;
    private List<Module> adapters;

    private boolean shouldDisableAutomaticShutdown;

    BtRuntimeBuilder(Config config) {
        this.config = config;
        this.modules = new HashMap<>();
        // default modules
        this.modules.put(ServiceModule.class, new ServiceModule(config));
        this.modules.put(ProtocolModule.class, new ProtocolModule());
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.0
     */
    public BtRuntimeBuilder module(Module adapter) {
        Objects.requireNonNull(adapter);
        if (adapters == null) {
            adapters = new ArrayList<>();
        }
        adapters.add(adapter);
        return this;
    }

    /**
     * Disable automatic runtime shutdown, when all clients have been stopped.
     *
     * @since 1.0
     */
    public BtRuntimeBuilder disableAutomaticShutdown() {
        this.shouldDisableAutomaticShutdown = true;
        return this;
    }

    /**
     * @since 1.0
     */
    public BtRuntime build() {
        BtRuntime runtime = new BtRuntime(createInjector(), config);
        if (shouldDisableAutomaticShutdown) {
            runtime.disableAutomaticShutdown();
        }
        return runtime;
    }

    private Injector createInjector() {

        Module[] standardModules = modules.values().toArray(new Module[modules.size()]);

        Injector injector;
        if (adapters != null && adapters.size() > 0) {
            adapters.forEach(adapter -> ContributionScanner.scanner().scan(adapter).forEach(this::applyContribution));
            Module customModule = Modules.override(standardModules).with(adapters);
            injector = Guice.createInjector(customModule);
        } else {
            injector = Guice.createInjector(standardModules);
        }
        return injector;
    }

    private <T extends Module> void applyContribution(Contribution<T> contribution) {
        @SuppressWarnings("unchecked")
        T requestedModule = (T) modules.get(contribution.getModuleType());
        if (requestedModule == null) {
            throw new BtException("Unknown requested module type: " + contribution.getModuleType());
        }
        contribution.apply(requestedModule);
    }
}
