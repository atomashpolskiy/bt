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

import bt.runtime.Config;
import bt.service.ExecutorServiceProvider;
import com.google.inject.Inject;

import static bt.net.InternetProtocolUtils.getLiteralIP;

public class TestExecutorServiceProvider extends ExecutorServiceProvider {
    private static final String PREFIX_FORMAT = "bt.it.executor-thread-%s-%d";

    private final String prefix;

    @Inject
    public TestExecutorServiceProvider(Config config) {
        this.prefix = String.format(PREFIX_FORMAT, getLiteralIP(config.getAcceptorAddress()), config.getAcceptorPort());
    }

    @Override
    protected String getNamePrefix() {
        return prefix;
    }
}
