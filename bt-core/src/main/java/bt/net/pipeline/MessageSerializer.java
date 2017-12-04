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

import bt.net.Peer;
import bt.protocol.EncodingContext;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.nio.ByteBuffer;

/**
 * Encodes and writes messages to a byte buffer.
 */
class MessageSerializer {

    private final EncodingContext context;
    private final MessageHandler<Message> protocol;

    public MessageSerializer(Peer peer,
                             MessageHandler<Message> protocol) {
        this.context = new EncodingContext(peer);
        this.protocol = protocol;
    }

    public boolean serialize(Message message, ByteBuffer buffer) {
        return protocol.encode(context, message, buffer);
    }
}
