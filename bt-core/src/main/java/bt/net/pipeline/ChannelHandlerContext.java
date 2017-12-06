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

package bt.net.pipeline;

/**
 * Provides the means to notify the messaging pipeline about certain events.
 *
 * @since 1.6
 */
public interface ChannelHandlerContext {

    /**
     * @since 1.6
     */
    ChannelPipeline pipeline();

    /**
     * Signal, that the channel is ready for reading
     *
     * @since 1.6
     */
    void fireChannelReady();

    /**
     * @since 1.6
     */
    void fireChannelRegistered();

    /**
     * @since 1.6
     */
    void fireChannelUnregistered();

    /**
     * @since 1.6
     */
    void fireChannelActive();

    /**
     * @since 1.6
     */
    void fireChannelInactive();

    // TODO: I guess this can be removed
    // we can instead use a series of ChannelPipeline.decode() invocations for the same effect
    void fireDataReceived();
}
