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

package bt;

import bt.runtime.BtRuntime;

/**
 * Main entry point to building Bt clients
 *
 * @since 1.0
 */
public class Bt {

    /**
     * Create a standalone client builder with a private runtime
     *
     * @since 1.1
     */
    public static StandaloneClientBuilder client() {
        return new StandaloneClientBuilder();
    }

    /**
     * Create a standard client builder with the provided runtime
     *
     * @since 1.1
     */
    public static BtClientBuilder client(BtRuntime runtime) {
        return new BtClientBuilder().runtime(runtime);
    }
}
