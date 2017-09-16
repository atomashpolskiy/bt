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

/**
 * Message agent, that is interested in receiving messages of type T
 *
 * @see bt.torrent.annotation.Consumes
 * @param <T> Message type
 * @since 1.0
 */
public interface MessageConsumer<T extends Message> {

    /**
     * @return Message type, that this consumer is interested in
     * @since 1.0
     */
    Class<T> getConsumedType();

    /**
     * @since 1.0
     */
    void consume(T message, MessageContext context);
}
