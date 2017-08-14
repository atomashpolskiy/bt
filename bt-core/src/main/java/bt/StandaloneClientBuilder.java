package bt;

import bt.module.BtModuleProvider;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import com.google.inject.Module;

/**
 * Builds a standalone client with a private runtime
 *
 * @since 1.1
 */
public class StandaloneClientBuilder extends TorrentClientBuilder<StandaloneClientBuilder> {

    private BtRuntimeBuilder runtimeBuilder;

    /**
     * @since 1.4
     */
    protected StandaloneClientBuilder() {
        this.runtimeBuilder = BtRuntime.builder();
    }

    /**
     * Set runtime configuration.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder config(Config config) {
        runtimeBuilder.config(config);
        return this;
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder module(Module module) {
        runtimeBuilder.module(module);
        return this;
    }

    /**
     * Contribute an extra module into the runtime.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder module(Class<? extends Module> moduleType) {
        runtimeBuilder.module(moduleType);
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
        runtimeBuilder.autoLoadModules();
        return this;
    }

    @Override
    public BtClient build() {
        runtime(runtimeBuilder.build());
        return super.build();
    }
}
