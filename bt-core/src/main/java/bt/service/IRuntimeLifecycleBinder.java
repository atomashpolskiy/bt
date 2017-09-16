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

package bt.service;

import java.util.function.Consumer;

/**
 * Application lifecycle management API.
 * Provides hooks for all major types of lifecycle events.
 *
 * All thread workers and executors must be registered via this service.
 *
 * @since 1.0
 */
public interface IRuntimeLifecycleBinder {

    /**
     * Lifecycle events
     *
     * @since 1.0
     */
    enum LifecycleEvent {

        /**
         * Runtime startup
         *
         * @since 1.0
         */
        STARTUP,

        /**
         * Runtime shutdown
         *
         * @since 1.0
         */
        SHUTDOWN
    }

    /**
     * Register a hook to run upon runtime startup
     *
     * @since 1.0
     */
    void onStartup(Runnable r);

    /**
     * Register a hook to run upon runtime startup
     *
     * @param description Human-readable description of the hook
     * @since 1.0
     */
    void onStartup(String description, Runnable r);

    /**
     * Register a hook to run upon runtime startup
     *
     * @param binding Hook
     * @since 1.1
     */
    void onStartup(LifecycleBinding binding);

    /**
     * Register an async hook to run upon runtime shutdown
     *
     * @since 1.0
     */
    void onShutdown(Runnable r);

    /**
     * Register an async hook to run upon runtime shutdown
     *
     * @param description Human-readable description of the hook
     * @since 1.0
     */
    void onShutdown(String description, Runnable r);

    /**
     * Register a hook to run upon runtime shutdown
     *
     * @param binding Hook
     * @since 1.1
     */
    void onShutdown(LifecycleBinding binding);

    /**
     * Register a hook to run upon runtime lifecycle phase
     *
     * @param event Lifecycle phase of the runtime
     * @param binding Hook
     * @since 1.1
     */
    void addBinding(LifecycleEvent event, LifecycleBinding binding);

    /**
     * Visitor interface for inspecting all registered hooks for a particular lifecycle event.
     *
     * @param event Lifecycle event
     * @param consumer Bindings consumer.
     * @since 1.1
     */
    void visitBindings(LifecycleEvent event, Consumer<LifecycleBinding> consumer);
}
