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
import bt.protocol.Piece;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.readInt;

public final class PieceHandler extends UniqueMessageHandler<Piece> {

    public PieceHandler() {
        super(Piece.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        return decodePiece(context, buffer, buffer.remaining());
    }

    @Override
    public boolean doEncode(EncodingContext context, Piece message, ByteBuffer buffer) {
        return writePiece(message, buffer);
    }

    // piece: <len=0009+X><id=7><index><begin><block>
    private static boolean writePiece(Piece message, ByteBuffer buffer) {
        int pieceIndex = message.getPieceIndex();
        int offset = message.getOffset();
        int length = message.getLength();
        if (buffer.remaining() < Integer.BYTES * 2 + length) {
            return false;
        }

        buffer.putInt(pieceIndex);
        buffer.putInt(offset);

        return message.writeBlockTo(buffer);
    }

    private static int decodePiece(DecodingContext context, ByteBufferView buffer, int length) {

        int consumed = 0;

        if (buffer.remaining() >= length) {

            int pieceIndex = readInt(buffer);
            int blockOffset = readInt(buffer);
            int blockLength = length - Integer.BYTES * 2;
            buffer.position(buffer.position() + blockLength);

            context.setMessage(new Piece(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }
}
