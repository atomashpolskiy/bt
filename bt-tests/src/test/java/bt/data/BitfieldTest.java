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

package bt.data;

import bt.metainfo.TorrentFile;
import bt.protocol.BitOrder;
import bt.protocol.Protocols;
import bt.torrent.BaseBitfieldTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitfieldTest extends BaseBitfieldTest {
    private static final int TEST_BITFIELD_LEN = 2931;

    @Test
    public void testBitfield() {
        final int numChunks = 8;
        LocalBitfield bitfield = new TestLocalBitfield(numChunks, null);
        bitfield.markLocalPieceVerified(0);
        bitfield.markLocalPieceVerified(2);
        bitfield.markLocalPieceVerified(7);

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
        assertFalse(bitfield.isComplete(6));
        assertTrue(bitfield.isComplete(7));

        assertTrue(bitfield.isVerified(0));
        assertFalse(bitfield.isVerified(1));
        assertTrue(bitfield.isVerified(2));
        assertFalse(bitfield.isVerified(3));
        assertFalse(bitfield.isVerified(4));
        assertFalse(bitfield.isVerified(5));
        assertFalse(bitfield.isVerified(6));
        assertTrue(bitfield.isVerified(7));
    }

    @Test
    public void testBitfield_NumberOfPiecesNotDivisibleBy8() {
        final int numChunks = 11;

        LocalBitfield bitfield = new TestLocalBitfield(numChunks, null);
        bitfield.markLocalPieceVerified(0);
        bitfield.markLocalPieceVerified(2);
        bitfield.markLocalPieceVerified(7);
        bitfield.markLocalPieceVerified(10);

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
        assertFalse(bitfield.isComplete(8));
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
    }

    @Test
    public void testBitfield_Skipped() {
        final int numChunks = 8;

        LocalBitfield bitfield = new TestLocalBitfield(numChunks, null);
        bitfield.markLocalPieceVerified(0);
        bitfield.markLocalPieceVerified(2);
        bitfield.markLocalPieceVerified(6);
        bitfield.markLocalPieceVerified(7);

        assertEquals(0, bitfield.getPiecesSkipped());

        BitSet piecesToSkip = new BitSet();

        piecesToSkip.set(0);
        bitfield.setSkippedPieces(piecesToSkip);

        assertEquals(1, bitfield.getPiecesSkipped());
        assertEquals(7, bitfield.getPiecesNotSkipped());
        assertEquals(4, bitfield.getPiecesRemaining());

        piecesToSkip.set(1);// ensure that this is copied
        assertEquals(1, bitfield.getPiecesSkipped());
        bitfield.setSkippedPieces(piecesToSkip);

        assertEquals(2, bitfield.getPiecesSkipped());
        assertEquals(6, bitfield.getPiecesNotSkipped());
        assertEquals(3, bitfield.getPiecesRemaining());

        bitfield.unskip(0);

        assertEquals(1, bitfield.getPiecesSkipped());
        assertEquals(7, bitfield.getPiecesNotSkipped());
        assertEquals(3, bitfield.getPiecesRemaining());
    }

    /**
     * Tests to make sure that peer bitfields are correctly decoded with little endian
     */
    @Test
    public void testReadPeerBitField() {
        final BitOrder endian = BitOrder.LITTLE_ENDIAN;
        final int testBitfieldLen = TEST_BITFIELD_LEN;
        runPeerBitfieldTest(endian, testBitfieldLen);
    }

    private void runPeerBitfieldTest(BitOrder endian, int testBitfieldLen) {
        Random random = new Random(1);
        byte[] testBytes = new byte[(testBitfieldLen + 7) / 8];
        random.nextBytes(testBytes);
        Bitfield bitfield = new PeerBitfield(testBytes, endian, testBitfieldLen);
        for (int i = 0; i < testBitfieldLen; i++) {
            Assert.assertEquals(Protocols.isSet(testBytes, endian, i), bitfield.isVerified(i));
        }
    }

    static class TestLocalBitfield extends LocalBitfield {
        public TestLocalBitfield(int piecesTotal, List<List<CompletableTorrentFile>> countdownFiles) {
            super(piecesTotal, countdownFiles);
        }

        @Override
        protected void fileFinishedCallback(TorrentFile tf) {
            // do nothing
        }
    }
}
