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

package bt.peerexchange;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.handler.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;

class PeerExchangeMessageHandler implements MessageHandler<PeerExchange> {

    private final Collection<Class<? extends PeerExchange>> supportedTypes;

    public PeerExchangeMessageHandler() {
        supportedTypes = Collections.singleton(PeerExchange.class);
    }

    @Override
    public Collection<Class<? extends PeerExchange>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<PeerExchange> readMessageType(ByteBuffer buffer) {
        return PeerExchange.class;
    }

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap messageContent = parser.readMap();
            PeerExchange message = PeerExchange.parse(messageContent);
            context.setMessage(message);
            return messageContent.getContent().length;
        }
    }

    @Override
    public boolean encode(EncodingContext context, PeerExchange message, ByteBuffer buffer) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            message.writeTo(bos);
        } catch (IOException e) {
            // can't happen
        }

        byte[] payload = bos.toByteArray();
        if (buffer.remaining() < payload.length) {
            return false;
        }

        buffer.put(payload);
        return true;
    }
}
