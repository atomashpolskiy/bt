/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

package bt.tracker.udp;

import bt.service.IRuntimeLifecycleBinder;
import bt.service.LifecycleBinding;

import java.util.function.Consumer;

/**
 * @author Oleg Ermolaev Date: 17.02.2018 1:49
 */
class MockRuntimeLifecycleBinder implements IRuntimeLifecycleBinder {
    private Runnable onShutdown;

    @Override
    public void onStartup(Runnable r) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onStartup(String description, Runnable r) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onStartup(LifecycleBinding binding) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onShutdown(Runnable runnable) {
        this.onShutdown = runnable;
    }

    @Override
    public void onShutdown(String description, Runnable runnable) {
        this.onShutdown = runnable;
    }

    @Override
    public void onShutdown(LifecycleBinding binding) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addBinding(LifecycleEvent event, LifecycleBinding binding) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitBindings(LifecycleEvent event, Consumer<LifecycleBinding> consumer) {
        throw new UnsupportedOperationException();
    }

    public void shutdown() {
        onShutdown.run();
    }
}
