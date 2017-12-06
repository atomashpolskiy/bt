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

import bt.protocol.Message;

/**
 * Provides API for direct messaging via a channel (usually non-blocking).
 *
 * @since 1.6
 */
public interface ChannelHandler {

    /**
     * @return true, if the message has been sent
     * @since 1.6
     */
    boolean send(Message message);

    /**
     * @return Message or null, if there are no incoming messages
     * @since 1.6
     */
    Message receive();

    /**
     * Request to read incoming data from the underlying channel.
     *
     * @since 1.6
     */
    void read();

    /**
     * @since 1.6
     */
    void register();

    /**
     * @since 1.6
     */
    void unregister();

    /**
     * @since 1.6
     */
    void activate();

    /**
     * @since 1.6
     */
    void deactivate();

    /**
     * Request to write pending outgoing data to the underlying channel.
     *
     * @since 1.6
     */
    void flush();

    /**
     * Request to close.
     * The procedure may involve unregistering, closing the underlying channel and releasing the resources.
     *
     * @since 1.6
     */
    void close();

    /**
     * @return true, if the handler has been closed
     * @since 1.6
     */
    boolean isClosed();
}
