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

import bt.processor.ProcessingContext;
import bt.processor.Processor;
import bt.processor.ProcessorFactory;
import bt.processor.listener.ListenerSource;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Provides basic capabilities to build a Bt client.
 *
 * @since 1.1
 */
public abstract class BaseClientBuilder<B extends BaseClientBuilder> {

    private BtRuntime runtime;
    private boolean shouldInitEagerly;

    /**
     * @since 1.1
     */
    protected BaseClientBuilder() {
    }

    /**
     * Set the runtime that the newly built client will be attached to.
     *
     * @param runtime Bt runtime
     * @since 1.4
     */
    @SuppressWarnings("unchecked")
    protected B runtime(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Missing runtime");
        return (B) this;
    }

    /**
     * Initialize the client eagerly.
     *
     * By default the client is initialized lazily
     * upon calling {@link BtClient#startAsync()} method or one of its' overloaded version.
     *
     * Initialization is implementation-specific and may include fetching torrent metainfo,
     * creating torrent and data descriptors, reserving storage space,
     * instantiating client-specific services, triggering DI injection, etc.
     *
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B initEagerly() {
        this.shouldInitEagerly = true;
        return (B) this;
    }

    /**
     * @since 1.1
     */
    public BtClient build() {
        Objects.requireNonNull(runtime, "Missing runtime");
        Supplier<BtClient> clientSupplier = () -> buildClient(runtime, buildProcessingContext(runtime));
        return shouldInitEagerly ? clientSupplier.get() : new LazyClient(clientSupplier);
    }

    /**
     * @since 1.4
     */
    protected abstract ProcessingContext buildProcessingContext(BtRuntime runtime);

    private <C extends ProcessingContext> BtClient buildClient(BtRuntime runtime, C context) {
        @SuppressWarnings("unchecked")
        Class<C> contextType = (Class<C>) context.getClass();

        ListenerSource<C> listenerSource = new ListenerSource<>(contextType);
        collectStageListeners(listenerSource);

        return new DefaultClient<>(runtime, processor(runtime, contextType), context, listenerSource);
    }

    /**
     * @since 1.5
     */
    protected abstract <C extends ProcessingContext> void collectStageListeners(ListenerSource<C> listenerSource);

    private <C extends ProcessingContext> Processor<C> processor(BtRuntime runtime, Class<C> contextType) {
        Processor<C> processor = runtime.service(ProcessorFactory.class).processor(contextType);
        if (processor == null) {
            throw new IllegalStateException("No processors found for context type: " + contextType.getName());
        }
        return processor;
    }
}
