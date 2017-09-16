/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
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
     * Create runtime builder with default config.
     *
     * @since 1.4
     */
    public BtRuntimeBuilder() {
        this.config = new Config();
    }

    /**
     * Create runtime builder with provided config.
     *
     * @param config Runtime config
     * @since 1.0
     */
    public BtRuntimeBuilder(Config config) {
        this.config = Objects.requireNonNull(config, "Missing runtime config");
    }

    /**
     * Set runtime config.
     *
     * @since 1.4
     */
    public BtRuntimeBuilder config(Config config) {
        this.config = Objects.requireNonNull(config, "Missing runtime config");
        return this;
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
        Objects.requireNonNull(adapterType);
        if (customProviders == null) {
            customProviders = new ArrayList<>();
        }
        customProviders.add(() -> {
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
        Collection<Module> standardModules = collectModules(standardProviders());

        Collection<Module> customModules;
        if (shouldAutoLoadModules) {
            Map<Class<? extends Module>, Module> autoLoadedProviders = mapByClass(collectModules(autoLoadedProviders()));
            Map<Class<? extends Module>, Module> customProviders = mapByClass(collectModules(customProviders()));

            autoLoadedProviders.forEach((k, v) -> {
                if (!customProviders.containsKey(k)) {
                    LOGGER.info("Auto-loading module {} with default configuration", k.getName());
                }
            });

            customProviders.forEach((k, v) -> {
                if (autoLoadedProviders.containsKey(k)) {
                    LOGGER.info("Overriding auto-loaded module {}", k.getName());
                } else {
                    LOGGER.info("Loading module {}", k.getName());
                }
            });
            autoLoadedProviders.putAll(customProviders);
            customModules = autoLoadedProviders.values();
        } else {
            customModules = collectModules(customProviders());
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
    private Collection<Module> collectModules(Collection<BtModuleProvider>... providers) {
        return Arrays.asList(providers).stream()
                .flatMap(Collection::stream)
                .map(BtModuleProvider::module)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Class<? extends T>, T> mapByClass(Collection<T> collection) {
        return collection.stream().collect(HashMap::new, (m, o) -> m.put((Class<T>)o.getClass(), o), Map::putAll);
    }

    private Collection<BtModuleProvider> standardProviders() {
        Collection<BtModuleProvider> standardProviders = new ArrayList<>();
        standardProviders.add(() -> new ServiceModule(config));
        standardProviders.add(ProtocolModule::new);
        return standardProviders;
    }

    private Collection<BtModuleProvider> customProviders() {
        if (customProviders == null) {
            return Collections.emptyList();
        } else {
            return customProviders.stream()
                    .map(BtRuntimeBuilder::nullCheckingProvider)
                    .collect(Collectors.toList());
        }
    }

    private Collection<BtModuleProvider> autoLoadedProviders() {
        Collection<BtModuleProvider> autoLoadedProviders = new ArrayList<>();
        ServiceLoader.load(BtModuleProvider.class).forEach(p -> {
            autoLoadedProviders.add(nullCheckingProvider(p));
        });
        return autoLoadedProviders;
    }

    private static BtModuleProvider nullCheckingProvider(BtModuleProvider provider) {
        return () -> Objects.requireNonNull(provider.module(), "Missing module in provider:" + provider.getClass().getName());
    }
}
