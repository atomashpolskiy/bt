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

import bt.protocol.Cancel;
import bt.protocol.EncodingContext;
import bt.protocol.InvalidMessageException;
import bt.protocol.DecodingContext;

import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class CancelHandler extends UniqueMessageHandler<Cancel> {

    public CancelHandler() {
        super(Cancel.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Cancel.class, 12, buffer.remaining());
        return decodeCancel(context, buffer);
    }

    @Override
    public boolean doEncode(EncodingContext context, Cancel message, ByteBuffer buffer) {
        return writeCancel(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
    }

    // cancel: <len=0013><id=8><index><begin><length>
    private static boolean writeCancel(int pieceIndex, int offset, int length, ByteBuffer buffer) {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")");
        }
        if (buffer.remaining() < Integer.BYTES * 3) {
            return false;
        }

        buffer.putInt(pieceIndex);
        buffer.putInt(offset);
        buffer.putInt(length);

        return true;
    }

    private static int decodeCancel(DecodingContext context, ByteBuffer buffer) {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (buffer.remaining() >= length) {

            int pieceIndex = Objects.requireNonNull(readInt(buffer));
            int blockOffset = Objects.requireNonNull(readInt(buffer));
            int blockLength = Objects.requireNonNull(readInt(buffer));

            context.setMessage(new Cancel(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }
}
