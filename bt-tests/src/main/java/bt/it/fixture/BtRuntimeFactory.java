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

package bt.it.fixture;

import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import com.google.inject.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class BtRuntimeFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtRuntimeFactory.class);

    private Collection<Module> modules;
    private List<BtRuntime> knownRuntimes;

    BtRuntimeFactory(Collection<Module> modules) {
        this.modules = modules;
        this.knownRuntimes = new ArrayList<>();
    }

    public BtRuntimeBuilder builder(Config config) {
        BtRuntimeBuilder builder = new BtRuntimeBuilder(config) {
            @Override
            public BtRuntime build() {
                modules.forEach(super::module);
                BtRuntime runtime = super.build();
                knownRuntimes.add(runtime);
                return runtime;
            }
        };
        builder.disableStandardExtensions();
        return builder;
    }

    public void shutdown() {
        knownRuntimes.forEach(runtime -> {
            try {
                runtime.shutdown();
            } catch (Exception e) {
                LOGGER.error("Runtime failed to shutdown", e);
            }
        });
    }
}
