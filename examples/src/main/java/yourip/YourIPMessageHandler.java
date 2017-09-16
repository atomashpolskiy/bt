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

package yourip;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.handler.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;

public class YourIPMessageHandler implements MessageHandler<YourIP> {

    @Override
    public boolean encode(EncodingContext context, YourIP message, ByteBuffer buffer) {
        boolean encoded = false;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            message.writeTo(bos);
            byte[] payload = bos.toByteArray();
            if (buffer.remaining() >= payload.length) {
                buffer.put(payload);
                encoded = true;
            }
        } catch (IOException e) {
            // can't happen
        }
        return encoded;
    }

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap message = parser.readMap();
            String address = new String(message.getValue().get(YourIP.addressField()).getContent(), Charset.forName("UTF-8"));
            context.setMessage(new YourIP(address));
            return message.getContent().length;
        }
    }

    @Override
    public Collection<Class<? extends YourIP>> getSupportedTypes() {
        return Collections.singleton(YourIP.class);
    }

    @Override
    public Class<? extends YourIP> readMessageType(ByteBuffer buffer) {
        return YourIP.class;
    }
}
