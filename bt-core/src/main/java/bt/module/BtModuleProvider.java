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

package bt.module;

import com.google.inject.Module;

/**
 * Provides the support for auto-loading of modules.
 * Module providers are specified in META-INF/services/bt.module.BtModuleProvider file.
 *
 * @see java.util.ServiceLoader
 * @since 1.1
 */
public interface BtModuleProvider {

    /**
     * @since 1.1
     */
    Module module();
}
