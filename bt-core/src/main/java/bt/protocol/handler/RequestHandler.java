/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.net.buffer.ByteBufferView;
import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.InvalidMessageException;
import bt.protocol.Request;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class RequestHandler extends UniqueMessageHandler<Request> {

    public RequestHandler() {
        super(Request.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Request.class, 12, buffer.remaining());
        return decodeRequest(context, buffer);
    }

    @Override
    public boolean doEncode(EncodingContext context, Request message, ByteBuffer buffer) {
        return writeRequest(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
    }

    // request: <len=0013><id=6><index><begin><length>
    private static boolean writeRequest(int pieceIndex, int offset, int length, ByteBuffer buffer) {

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

    private static int decodeRequest(DecodingContext context, ByteBufferView buffer) {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (buffer.remaining() >= length) {

            int pieceIndex = readInt(buffer);
            int blockOffset = readInt(buffer);
            int blockLength = readInt(buffer);

            context.setMessage(new Request(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }
}
