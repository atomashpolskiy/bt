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

import bt.protocol.EncodingContext;
import bt.protocol.Have;
import bt.protocol.InvalidMessageException;
import bt.protocol.DecodingContext;

import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class HaveHandler extends UniqueMessageHandler<Have> {

    public HaveHandler() {
        super(Have.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Have.class, 4, buffer.remaining());
        return decodeHave(context, buffer);
    }

    @Override
    public boolean doEncode(EncodingContext context, Have message, ByteBuffer buffer) {
        return writeHave(message.getPieceIndex(), buffer);
    }

    // have: <len=0005><id=4><piece index>
    private static boolean writeHave(int pieceIndex, ByteBuffer buffer) {
        if (pieceIndex < 0) {
            throw new InvalidMessageException("Invalid piece index: " + pieceIndex);
        }
        if (buffer.remaining() < Integer.BYTES) {
            return false;
        }

        buffer.putInt(pieceIndex);
        return true;
    }

    private static int decodeHave(DecodingContext context, ByteBuffer buffer) {

        int consumed = 0;
        int length = Integer.BYTES;

        if (buffer.remaining() >= length) {
            Integer pieceIndex = Objects.requireNonNull(readInt(buffer));
            context.setMessage(new Have(pieceIndex));
            consumed = length;
        }

        return consumed;
    }
}
