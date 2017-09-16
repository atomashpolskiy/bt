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
 * Base class for chained processing stage implementations.
 *
 * @param <C> Type of processing context
 * @since 1.3
 */
public abstract class RoutingProcessingStage<C extends ProcessingContext> implements ProcessingStage<C> {

    private final ProcessingStage<C> next;

    /**
     * @param next Default next processing stage
     * @since 1.3
     */
    public RoutingProcessingStage(ProcessingStage<C> next) {
        this.next = next;
    }

    @Override
    public ProcessingStage<C> execute(C context) {
        return doExecute(context, next);
    }

    /**
     * Execute current stage and calculate the next stage.
     *
     * In most cases the implementing class should just return the default next stage,
     * that is passed as the second argument to this method. However, in some cases it can override
     * the actual next stage, e.g. to switch to the exception path in case of a processing error.
     *
     * @param context Processing context
     * @param next Default next stage (usually statically configured in ProcessorFactory)
     * @return Actual next stage
     * @since 1.5
     */
    protected abstract ProcessingStage<C> doExecute(C context, ProcessingStage<C> next);
}
