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

package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;

/**
 * @since 1.3
 */
public interface MessageRouter {

    /**
     * Route a message to consumers.
     *
     * @since 1.3
     */
    void consume(Message message, MessageContext context);

    /**
     * Request a message from producers.
     *
     * @since 1.3
     */
    void produce(Consumer<Message> messageConsumer, MessageContext context);

    /**
     * Add a messaging agent, that can act as a message consumer and/or producer.
     *
     * @see bt.torrent.annotation.Consumes
     * @see bt.torrent.annotation.Produces
     * @since 1.3
     */
    void registerMessagingAgent(Object agent);

    /**
     * Remove a messaging agent, if it's registered in this message router.
     *
     * @since 1.3
     */
    void unregisterMessagingAgent(Object agent);
}
