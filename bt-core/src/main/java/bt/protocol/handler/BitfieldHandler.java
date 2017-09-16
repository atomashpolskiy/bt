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

package bt.protocol.handler;

import bt.protocol.Bitfield;
import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;

import java.nio.ByteBuffer;

public final class BitfieldHandler extends UniqueMessageHandler<Bitfield> {

    public BitfieldHandler() {
        super(Bitfield.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        return decodeBitfield(context, buffer, buffer.remaining());
    }

    @Override
    public boolean doEncode(EncodingContext context, Bitfield message, ByteBuffer buffer) {
        if (buffer.remaining() < message.getBitfield().length) {
            return false;
        }
        buffer.put(message.getBitfield());
        return true;
    }

    // bitfield: <len=0001+X><id=5><bitfield>
    private static int decodeBitfield(DecodingContext context, ByteBuffer buffer, int length) {

        int consumed = 0;

        if (buffer.remaining() >= length) {
            byte[] bitfield = new byte[length];
            buffer.get(bitfield);
            context.setMessage(new Bitfield(bitfield));
            consumed = length;
        }

        return consumed;
    }
}
