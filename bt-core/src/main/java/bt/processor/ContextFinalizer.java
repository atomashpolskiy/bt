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
 * Used to finalize context and cleanup resources,
 * when processing completes normally or terminates abruptly due to error
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public interface ContextFinalizer<C extends ProcessingContext> {

    /**
     * Perform finalization and cleanup.
     *
     * @param context Processing context, that should be finalized
     * @since 1.5
     */
    void finalizeContext(C context);
}
