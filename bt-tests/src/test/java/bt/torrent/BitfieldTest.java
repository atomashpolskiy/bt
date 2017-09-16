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

package bt.torrent;

import bt.data.Bitfield;
import bt.data.ChunkDescriptor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BitfieldTest extends BaseBitfieldTest {

    @Test
    public void testBitfield() {

        List<ChunkDescriptor> chunks = Arrays.asList(completeChunk, emptyChunk, emptyChunk, completeChunk,
                emptyChunk, emptyChunk, emptyChunk, completeChunk);
        Bitfield bitfield = new Bitfield(chunks);
        bitfield.markVerified(0);
        bitfield.markVerified(3);
        bitfield.markVerified(7);

        byte expectedBitfield = (byte) (1 + (0b1 << 4) + (0b1 << 7));

        assertArrayEquals(new byte[]{expectedBitfield}, bitfield.getBitmask());
        assertNotEquals(0, bitfield.getPiecesComplete());
        assertEquals(5, bitfield.getPiecesRemaining());
    }

    @Test
    public void testBitfield_AllEmpty() {

        ChunkDescriptor[] chunkArray = new ChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);

        List<ChunkDescriptor> chunks = Arrays.asList(chunkArray);
        Bitfield bitfield = new Bitfield(chunks);

        assertArrayEquals(new byte[]{0,0}, bitfield.getBitmask());
        assertEquals(0, bitfield.getPiecesComplete());
        assertEquals(12, bitfield.getPiecesRemaining());
    }
}
