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

package bt.protocol;

import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * Protocol is responsible for determining message types.
 * When a message is received from peer,
 * the protocol is the first entity in the message processing chain
 * to correctly determine the type of message
 * and delegate the decoding of the message
 * to a particular message handler.
 *
 * @param <T> Common supertype for all message types, supported by this protocol.
 * @since 1.0
 */
public interface Protocol<T> {

    /**
     * @return All message types, supported by this protocol.
     * @since 1.0
     */
    Collection<Class<? extends T>> getSupportedTypes();

    /**
     * Tries to determine the message type based on the (part of the) message available in the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     * @since 1.0
     */
    Class<? extends T> readMessageType(ByteBuffer buffer);
}
