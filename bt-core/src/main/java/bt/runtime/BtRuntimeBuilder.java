package bt.runtime;

import bt.BtException;
import bt.module.BtModuleProvider;
import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Runtime builder.
 *
 * @since 1.0
 */
public class BtRuntimeBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtRuntimeBuilder.class);

    private Config config;
    private Collection<BtModuleProvider> customProviders;

    private boolean shouldDisableAutomaticShutdown;
    private boolean shouldAutoLoadModules;

    /**
     * Create runtime builder with provided config.
     *
     * @param config Runtime config
     * @since 1.0
     */
    public BtRuntimeBuilder(Config config) {
        this.config = config;
        this.customProviders = new ArrayList<>();
    }

    /**
     * Get this builder's config.
     *
     * @return Runtime config
     * @since 1.0
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.0
     */
    public BtRuntimeBuilder module(Module adapter) {
        Objects.requireNonNull(adapter);
        if (customProviders == null) {
            customProviders = new ArrayList<>();
        }
        customProviders.add(() -> adapter);
        return this;
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.1
     */
    public BtRuntimeBuilder module(Class<? extends Module> adapterType) {
        this.customProviders.add(() -> {
            try {
                return adapterType.newInstance();
            } catch (Exception e) {
                throw new BtException("Failed to instantiate custom module: " + adapterType.getName(), e);
            }
        });
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
     * If this option is set, Bt will use the service loading mechanism
     * to load any modules that are available on application's classpath.
     *
     * To support auto-loading a module should expose {@link BtModuleProvider} provider.
     * Auto-loaded modules will be used in default configuration.
     *
     * @since 1.1
     */
    public BtRuntimeBuilder autoLoadModules() {
        this.shouldAutoLoadModules = true;
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

    @SuppressWarnings("unchecked")
    private Injector createInjector() {
        Collection<Module> standardModules = collectModules(this::standardProviders);

        Collection<Module> customModules;
        if (shouldAutoLoadModules) {
            customModules = collectModules(this::customProviders, this::autoLoadedProviders);
        } else {
            customModules = collectModules(this::customProviders);
        }

        Injector injector;
        if (customModules.size() > 0) {
            Module customModule = Modules.override(standardModules).with(customModules);
            injector = Guice.createInjector(customModule);
        } else {
            injector = Guice.createInjector(standardModules);
        }
        return injector;
    }

    @SuppressWarnings("unchecked")
    private Collection<Module> collectModules(Supplier<Collection<BtModuleProvider>>... providers) {
        return Arrays.asList(providers).stream()
                .map(Supplier::get)
                .flatMap(Collection::stream)
                .map(BtModuleProvider::module)
                .collect(Collectors.toList());
    }

    private Collection<BtModuleProvider> standardProviders() {
        Collection<BtModuleProvider> standardProviders = new ArrayList<>();
        standardProviders.add(() -> new ServiceModule(getConfig()));
        standardProviders.add(ProtocolModule::new);
        return standardProviders;
    }

    private Collection<BtModuleProvider> customProviders() {
        if (customProviders == null) {
            return Collections.emptyList();
        } else {
            return customProviders.stream().map(p -> (BtModuleProvider) () -> {
                Module m = Objects.requireNonNull(p.module(), "Missing module");
                LOGGER.info("Loading module {}", m.getClass().getName());
                return m;
            }).collect(Collectors.toList());
        }
    }

    private Collection<BtModuleProvider> autoLoadedProviders() {
        Collection<BtModuleProvider> autoLoadedProviders = new ArrayList<>();
        ServiceLoader.load(BtModuleProvider.class).forEach(p -> {
            autoLoadedProviders.add(() -> {
                Module m = Objects.requireNonNull(p.module(), "Missing module");
                LOGGER.info("Auto-loading module {} with default configuration", m.getClass().getName());
                return m;
            });
        });
        return autoLoadedProviders;
    }
}
