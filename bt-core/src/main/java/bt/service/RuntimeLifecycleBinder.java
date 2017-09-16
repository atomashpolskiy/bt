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
