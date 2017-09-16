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

package bt.processor;

/**
 * Builds processors for different context types.
 *
 * @since 1.5
 */
public interface ProcessorFactory {

    /**
     * Build a processor for a given context type.
     *
     * @param contextType Processing context type
     * @return Processor for a given context type or null, if this context type is not supported
     * @since 1.3
     */
    <C extends ProcessingContext> Processor<C> processor(Class<C> contextType);
}
