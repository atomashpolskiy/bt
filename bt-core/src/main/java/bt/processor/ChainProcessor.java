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

import bt.processor.listener.ListenerSource;
import bt.processor.listener.ProcessingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

/**
 * Base implementation of a generic asynchronous executor of processing chains.
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public class ChainProcessor<C extends ProcessingContext> implements Processor<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChainProcessor.class);

    private ProcessingStage<C> chainHead;
    private ExecutorService executor;
    private Optional<ContextFinalizer<C>> finalizer;

    /**
     * Create processor for a given processing chain.
     *
     * @param chainHead First stage
     * @param executor Asynchronous facility to use for executing the processing chain
     * @since 1.5
     */
    public ChainProcessor(ProcessingStage<C> chainHead,
                          ExecutorService executor) {
        this(chainHead, executor, Optional.empty());
    }

    /**
     * Create processor for a given processing chain.
     *
     * @param chainHead First stage
     * @param executor Asynchronous facility to use for executing the processing chain
     * @param finalizer Context finalizer, that will be called,
     *                  when torrent processing completes normally or terminates abruptly due to error
     * @since 1.5
     */
    public ChainProcessor(ProcessingStage<C> chainHead,
                          ExecutorService executor,
                          ContextFinalizer<C> finalizer) {
        this(chainHead, executor, Optional.of(finalizer));
    }

    private ChainProcessor(ProcessingStage<C> chainHead,
                          ExecutorService executor,
                          Optional<ContextFinalizer<C>> finalizer) {
        this.chainHead = chainHead;
        this.finalizer = finalizer;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<?> process(C context, ListenerSource<C> listenerSource) {
        Runnable r = () -> executeStage(chainHead, context, listenerSource);
        return CompletableFuture.runAsync(r, executor);
    }

    private void executeStage(ProcessingStage<C> chainHead,
                              C context,
                              ListenerSource<C> listenerSource) {
        ProcessingEvent stageFinished = chainHead.after();
        Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> listeners;
        if (stageFinished != null) {
            listeners = listenerSource.getListeners(stageFinished);
        } else {
            listeners = Collections.emptyList();
        }

        ProcessingStage<C> next = doExecute(chainHead, context, listeners);
        if (next != null) {
            executeStage(next, context, listenerSource);
        }
    }

    private ProcessingStage<C> doExecute(ProcessingStage<C> stage,
                                         C context,
                                         Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> listeners) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Processing next stage: torrent ID (%s), stage (%s)",
                    context.getTorrentId().orElse(null), stage.getClass().getName()));
        }

        ProcessingStage<C> next;
        try {
            next = stage.execute(context);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Finished processing stage: torrent ID (%s), stage (%s)",
                        context.getTorrentId().orElse(null), stage.getClass().getName()));
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Processing failed with error: torrent ID (%s), stage (%s)",
                    context.getTorrentId().orElse(null), stage.getClass().getName()), e);
            finalizer.ifPresent(f -> f.finalizeContext(context));
            throw e;
        }

        for (BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener : listeners) {
            try {
                // TODO: different listeners may return different next stages (including nulls)
                next = listener.apply(context, next);
            } catch (Exception e) {
                LOGGER.error("Listener invocation failed", e);
            }
        }

        if (next == null) {
            finalizer.ifPresent(f -> f.finalizeContext(context));
        }
        return next;
    }
}
