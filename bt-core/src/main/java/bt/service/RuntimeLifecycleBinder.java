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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class RuntimeLifecycleBinder implements IRuntimeLifecycleBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeLifecycleBinder.class);

    private final Map<LifecycleEvent, List<LifecycleBinding>> bindings;
    private final EnumSet<LifecycleEvent> eventsRun = EnumSet.noneOf(LifecycleEvent.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public RuntimeLifecycleBinder() {
        bindings = new HashMap<>();
        for (LifecycleEvent event : LifecycleEvent.values()) {
            bindings.put(event, new ArrayList<>());
        }
    }

    @Override
    public void onStartup(Runnable r) {
        addBinding(LifecycleEvent.STARTUP, LifecycleBinding.bind(r).build());
    }

    @Override
    public void onStartup(String description, Runnable r) {
        addBinding(LifecycleEvent.STARTUP, LifecycleBinding.bind(r).description(description).build());
    }

    @Override
    public void onStartup(LifecycleBinding binding) {
        addBinding(LifecycleEvent.STARTUP, binding);
    }

    @Override
    public void onShutdown(Runnable r) {
        addBinding(LifecycleEvent.SHUTDOWN, LifecycleBinding.bind(r).async().build());
    }

    @Override
    public void onShutdown(String description, Runnable r) {
        addBinding(LifecycleEvent.SHUTDOWN,
                LifecycleBinding.bind(r).description(description).async().build());
    }

    @Override
    public void onShutdown(LifecycleBinding binding) {
        addBinding(LifecycleEvent.SHUTDOWN, binding);
    }

    @Override
    public void addBinding(LifecycleEvent event, LifecycleBinding binding) {
        lock.readLock().lock();
        try {
            if (this.eventsRun.contains(event)) {
                // we already ran this lifecycle event, so call the binding
                binding.getRunnable().run();
            } else {
                bindings.get(event).add(binding);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void visitBindings(LifecycleEvent event, Consumer<LifecycleBinding> consumer) {
        lock.writeLock().lock();
        try {
            if (!this.eventsRun.add(event)) {
                LOGGER.error("LifecycleEvent " + event + " has already been run. Hooks will not called again.");
                return;
            }
            bindings.get(event).forEach(consumer::accept);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
