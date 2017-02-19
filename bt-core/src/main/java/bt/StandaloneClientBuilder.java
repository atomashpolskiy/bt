package bt;

import bt.module.BtModuleProvider;
import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import com.google.inject.Module;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a standalone client with a private runtime
 *
 * @since 1.1
 */
public class StandaloneClientBuilder extends BaseClientBuilder<StandaloneClientBuilder> {

    static StandaloneClientBuilder standalone() {
        return new StandaloneClientBuilder();
    }

    private Config config;
    private Set<Module> modules;
    private Set<Class<? extends Module>> moduleTypes;
    private boolean shouldAutoLoadModules;

    private StandaloneClientBuilder() {
        // set default config
        this.config = new Config();
    }

    /**
     * Set runtime configuration.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder config(Config config) {
        this.config = Objects.requireNonNull(config, "Missing runtime config");
        return this;
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder module(Module module) {
        Objects.requireNonNull(module);
        if (modules == null) {
            modules = new HashSet<>();
        }
        modules.add(module);
        return this;
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder module(Class<? extends Module> moduleType) {
        Objects.requireNonNull(moduleType);
        if (moduleTypes == null) {
            moduleTypes = new HashSet<>();
        }
        moduleTypes.add(moduleType);
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
    public StandaloneClientBuilder autoLoadModules() {
        this.shouldAutoLoadModules = true;
        return this;
    }

    @Override
    protected BtRuntime getRuntime() {
        BtRuntimeBuilder runtimeBuilder = BtRuntime.builder(config);

        if (modules != null) {
            modules.forEach(runtimeBuilder::module);
        }

        if (moduleTypes != null) {
            moduleTypes.forEach(runtimeBuilder::module);
        }

        if (shouldAutoLoadModules) {
            runtimeBuilder.autoLoadModules();
        }

        return runtimeBuilder.build();
    }
}
