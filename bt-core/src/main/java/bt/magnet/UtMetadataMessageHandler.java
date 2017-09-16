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

package bt.magnet;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.handler.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

/**
 * @since 1.3
 */
public class UtMetadataMessageHandler implements MessageHandler<UtMetadata> {
    private final Collection<Class<? extends UtMetadata>> supportedTypes = Collections.singleton(UtMetadata.class);

    @Override
    public boolean encode(EncodingContext context, UtMetadata message, ByteBuffer buffer) {
        boolean encoded = false;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            writeMessage(message, bos);
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

    private void writeMessage(UtMetadata message, OutputStream out) throws IOException {
        BEMap m = new BEMap(null, new HashMap<String, BEObject<?>>() {{
            put(UtMetadata.messageTypeField(), new BEInteger(null, BigInteger.valueOf(message.getType().id())));
            put(UtMetadata.pieceIndexField(), new BEInteger(null, BigInteger.valueOf(message.getPieceIndex())));
            if (message.getData().isPresent()) {
                put(UtMetadata.totalSizeField(), new BEInteger(null, BigInteger.valueOf(message.getTotalSize().get())));
            }
        }});
        m.writeTo(out);
        if (message.getData().isPresent()) {
            out.write(message.getData().get());
        }
    }

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap m = parser.readMap();
            int length = m.getContent().length;
            UtMetadata.Type messageType = getMessageType(m);
            int pieceIndex = getPieceIndex(m);
            switch (messageType) {
                case REQUEST: {
                    context.setMessage(UtMetadata.request(pieceIndex));
                    return length;
                }
                case DATA: {
                    byte[] data = Arrays.copyOfRange(payload, length, payload.length);
                    context.setMessage(UtMetadata.data(pieceIndex, getTotalSize(m), data));
                    return payload.length;
                }
                case REJECT: {
                    context.setMessage(UtMetadata.reject(pieceIndex));
                    return length;
                }
                default: {
                    throw new IllegalStateException("Unknown message type: " + messageType.name());
                }
            }
        }
    }

    private UtMetadata.Type getMessageType(BEMap m) {
        BEInteger type = (BEInteger) m.getValue().get(UtMetadata.messageTypeField());
        int typeId = type.getValue().intValueExact();
        return UtMetadata.Type.forId(typeId);
    }

    private int getPieceIndex(BEMap m) {
        return getIntAttribute(UtMetadata.pieceIndexField(), m);
    }

    private int getTotalSize(BEMap m) {
        return getIntAttribute(UtMetadata.totalSizeField(), m);
    }

    private int getIntAttribute(String name, BEMap m) {
        BEInteger value = ((BEInteger) m.getValue().get(name));
        if (value == null) {
            throw new IllegalStateException("Message attribute is missing: " + name);
        }
        return value.getValue().intValueExact();
    }

    @Override
    public Collection<Class<? extends UtMetadata>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends UtMetadata> readMessageType(ByteBuffer buffer) {
        return UtMetadata.class;
    }
}
