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
import bt.protocol.BitOrder;
import bt.protocol.Protocols;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static bt.TestUtil.assertExceptionWithMessage;
import static bt.data.Bitfield.PieceStatus.COMPLETE;
import static bt.data.Bitfield.PieceStatus.COMPLETE_VERIFIED;
import static bt.data.Bitfield.PieceStatus.INCOMPLETE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitfieldTest extends BaseBitfieldTest {

    @Test
    public void testBitfield() {
        List<ChunkDescriptor> chunks = Arrays.asList(
                completeChunk,
                emptyChunk,
                completeChunk,
                emptyChunk,
                emptyChunk,
                emptyChunk,
                completeChunk, // piece #6 is not verified
                completeChunk);

        Bitfield bitfield = new Bitfield(chunks);
        bitfield.markVerified(0);
        bitfield.markVerified(2);
        bitfield.markVerified(7);

        byte expectedBitfieldLE = (byte) 0b10100001;
        byte expectedBitfieldBE = (byte) 0b10000101;

        assertArrayEquals(new byte[]{expectedBitfieldLE}, bitfield.toByteArray(BitOrder.LITTLE_ENDIAN));
        assertArrayEquals(new byte[]{expectedBitfieldBE}, bitfield.toByteArray(BitOrder.BIG_ENDIAN));

        assertEquals(8, bitfield.getPiecesTotal());
        assertEquals(3, bitfield.getPiecesComplete());
        assertEquals(5, bitfield.getPiecesIncomplete());
        assertEquals(0, bitfield.getPiecesSkipped());
        assertEquals(8, bitfield.getPiecesNotSkipped());
        assertEquals(5, bitfield.getPiecesRemaining());

        BitSet bitmask = bitfield.getBitmask();
        assertEquals(3, bitmask.cardinality());
        assertEquals(8, bitmask.length());

        assertTrue(bitfield.isComplete(0));
        assertFalse(bitfield.isComplete(1));
        assertTrue(bitfield.isComplete(2));
        assertFalse(bitfield.isComplete(3));
        assertFalse(bitfield.isComplete(4));
        assertFalse(bitfield.isComplete(5));
        assertTrue(bitfield.isComplete(6));
        assertTrue(bitfield.isComplete(7));

        assertTrue(bitfield.isVerified(0));
        assertFalse(bitfield.isVerified(1));
        assertTrue(bitfield.isVerified(2));
        assertFalse(bitfield.isVerified(3));
        assertFalse(bitfield.isVerified(4));
        assertFalse(bitfield.isVerified(5));
        assertFalse(bitfield.isVerified(6));
        assertTrue(bitfield.isVerified(7));

        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(0));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(1));
        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(2));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(3));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(4));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(5));
        assertEquals(COMPLETE, bitfield.getPieceStatus(6));
        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(7));
    }

    @Test
    public void testBitfield_NumberOfPiecesNotDivisibleBy8() {
        List<ChunkDescriptor> chunks = Arrays.asList(
                completeChunk,
                emptyChunk,
                completeChunk,
                emptyChunk,
                emptyChunk,
                emptyChunk,
                emptyChunk,
                completeChunk,

                completeChunk, // piece #8 is not verified
                emptyChunk,
                completeChunk);

        Bitfield bitfield = new Bitfield(chunks);
        bitfield.markVerified(0);
        bitfield.markVerified(2);
        bitfield.markVerified(7);
        bitfield.markVerified(10);

        short expectedBitfieldLE = (short) 0b10100001_00100000;
        short expectedBitfieldBE = (short) 0b10000101_00000100;

        assertArrayEquals(Protocols.getShortBytes(expectedBitfieldLE), bitfield.toByteArray(BitOrder.LITTLE_ENDIAN));
        assertArrayEquals(Protocols.getShortBytes(expectedBitfieldBE), bitfield.toByteArray(BitOrder.BIG_ENDIAN));

        assertEquals(11, bitfield.getPiecesTotal());
        assertEquals(4, bitfield.getPiecesComplete());
        assertEquals(7, bitfield.getPiecesIncomplete());
        assertEquals(0, bitfield.getPiecesSkipped());
        assertEquals(11, bitfield.getPiecesNotSkipped());
        assertEquals(7, bitfield.getPiecesRemaining());

        BitSet bitmask = bitfield.getBitmask();
        assertEquals(4, bitmask.cardinality());
        assertEquals(11, bitmask.length());

        assertTrue(bitfield.isComplete(0));
        assertFalse(bitfield.isComplete(1));
        assertTrue(bitfield.isComplete(2));
        assertFalse(bitfield.isComplete(3));
        assertFalse(bitfield.isComplete(4));
        assertFalse(bitfield.isComplete(5));
        assertFalse(bitfield.isComplete(6));
        assertTrue(bitfield.isComplete(7));
        assertTrue(bitfield.isComplete(8));
        assertFalse(bitfield.isComplete(9));
        assertTrue(bitfield.isComplete(10));

        assertTrue(bitfield.isVerified(0));
        assertFalse(bitfield.isVerified(1));
        assertTrue(bitfield.isVerified(2));
        assertFalse(bitfield.isVerified(3));
        assertFalse(bitfield.isVerified(4));
        assertFalse(bitfield.isVerified(5));
        assertFalse(bitfield.isVerified(6));
        assertTrue(bitfield.isVerified(7));
        assertFalse(bitfield.isVerified(8));
        assertFalse(bitfield.isVerified(9));
        assertTrue(bitfield.isVerified(10));

        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(0));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(1));
        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(2));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(3));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(4));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(5));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(6));
        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(7));
        assertEquals(COMPLETE, bitfield.getPieceStatus(8));
        assertEquals(INCOMPLETE, bitfield.getPieceStatus(9));
        assertEquals(COMPLETE_VERIFIED, bitfield.getPieceStatus(10));
    }

    @Test
    public void testBitfield_Skipped() {
        List<ChunkDescriptor> chunks = Arrays.asList(
                completeChunk,
                emptyChunk,
                completeChunk,
                emptyChunk,
                emptyChunk,
                emptyChunk,
                completeChunk,
                completeChunk);

        Bitfield bitfield = new Bitfield(chunks);
        bitfield.markVerified(0);
        bitfield.markVerified(2);
        bitfield.markVerified(6);
        bitfield.markVerified(7);

        assertEquals(0, bitfield.getPiecesSkipped());

        bitfield.skip(0);

        assertEquals(1, bitfield.getPiecesSkipped());
        assertEquals(7, bitfield.getPiecesNotSkipped());
        assertEquals(4, bitfield.getPiecesRemaining());

        bitfield.skip(1);

        assertEquals(2, bitfield.getPiecesSkipped());
        assertEquals(6, bitfield.getPiecesNotSkipped());
        assertEquals(3, bitfield.getPiecesRemaining());

        bitfield.unskip(0);

        assertEquals(1, bitfield.getPiecesSkipped());
        assertEquals(7, bitfield.getPiecesNotSkipped());
        assertEquals(3, bitfield.getPiecesRemaining());
    }

    @Test
    public void testBitfield_Exceptional_markVerified_NotComplete() {
        List<ChunkDescriptor> chunks = Arrays.asList(completeChunk, emptyChunk);
        Bitfield bitfield = new Bitfield(chunks);
        assertExceptionWithMessage(it -> {
            bitfield.markVerified(1);
            return null;
        }, "Chunk is not complete: 1");
    }
}
