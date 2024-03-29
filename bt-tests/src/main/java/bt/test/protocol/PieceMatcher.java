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

package bt.test.protocol;

import bt.protocol.Piece;

import java.nio.ByteBuffer;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

final class PieceMatcher implements BiPredicate<Piece, Piece> {

    @Override
    public boolean test(Piece piece, Piece piece2) {
        assertEquals(piece.getPieceIndex(), piece.getPieceIndex());
        assertEquals(piece.getOffset(), piece.getOffset());

        ByteBuffer buf1 = ByteBuffer.allocate(piece.getLength());
        ByteBuffer buf2 = ByteBuffer.allocate(piece.getLength());

        piece.writeBlockTo(buf1);
        piece2.writeBlockTo(buf2);

        assertArrayEquals(buf1.array(), buf2.array());
        return true;
    }
}
