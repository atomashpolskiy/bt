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

package bt.processor.listener;

import bt.processor.ProcessingContext;
import bt.processor.ProcessingStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Accumulates processing event listeners for a particular type of processing context
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public class ListenerSource<C extends ProcessingContext> {

    private Class<C> contextType;
    private Map<ProcessingEvent, Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>>> listeners;

    /**
     * Create an instance of listener source for a particular type of processing context
     *
     * @param contextType Type of processing context
     * @since 1.5
     */
    public ListenerSource(Class<C> contextType) {
        this.contextType = contextType;
        this.listeners = new HashMap<>();
    }

    /**
     * @since 1.5
     */
    public Class<C> getContextType() {
        return contextType;
    }

    /**
     * Add processing event listener.
     *
     * Processing event listener is a generic {@link BiFunction},
     * that accepts the processing context and default next stage
     * and returns the actual next stage (i.e. it can also be considered a router).
     *
     * @param event Type of processing event to be notified of
     * @param listener Routing function
     * @since 1.5
     */
    public void addListener(ProcessingEvent event, BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener) {
        listeners.computeIfAbsent(event, it -> new ArrayList<>()).add(listener);
    }

    /**
     * @param event Type of processing event
     * @return Collection of listeners, that are interested in being notified of a given event
     * @since 1.5
     */
    public Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> getListeners(ProcessingEvent event) {
        Objects.requireNonNull(event);
        return listeners.getOrDefault(event, Collections.emptyList());
    }
}
