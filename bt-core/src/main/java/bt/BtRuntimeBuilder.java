package bt;

import bt.module.Contribution;
import bt.module.ContributionScanner;
import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import bt.service.IShutdownService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public class BtRuntimeBuilder {

    public static BtRuntimeBuilder builder() {
        return new BtRuntimeBuilder();
    }

    private Map<Class<? extends Module>, Module> modules;
    private List<Module> adapters;

    private BtRuntimeBuilder() {
        modules = new HashMap<>();
        // default modules
        modules.put(ServiceModule.class, new ServiceModule());
        modules.put(ProtocolModule.class, new ProtocolModule());
    }

    public BtRuntimeBuilder module(Module adapter) {
        Objects.requireNonNull(adapter);
        if (adapters == null) {
            adapters = new ArrayList<>();
        }
        adapters.add(adapter);
        return this;
    }

    public BtRuntime build() {
        Injector injector = createInjector();
        return new BtRuntime(injector);
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

        IShutdownService shutdownService = injector.getInstance(IShutdownService.class);
        ExecutorService executor = injector.getInstance(ExecutorService.class);
        shutdownService.addShutdownHook(executor::shutdownNow);

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
