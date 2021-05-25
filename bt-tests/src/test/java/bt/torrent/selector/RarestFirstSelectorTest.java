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

package bt.torrent.selector;

import bt.test.torrent.selector.UpdatablePieceStatistics;
import org.junit.Before;
import org.junit.Test;

import java.util.BitSet;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RarestFirstSelectorTest {
    private static final int NUM_PIECES = 8;
    private BitSet relevantPieces;

    @Before
    public void setUp() throws Exception {
        relevantPieces = new BitSet(NUM_PIECES);
        relevantPieces.set(0, NUM_PIECES);
    }

    /**
     * Simple test cases to make sure that the rarest first selector selects pieces correctly.
     */
    @Test
    public void testSelector() {
        final RarestFirstSelector rarest = RarestFirstSelector.rarest();
        testSelector(rarest);
    }

    /**
     * Simple test cases to make sure that the randomized rarest first selector selects pieces correctly.
     */
    @Test
    public void testRandomizedSelector() {
        final RarestFirstSelector rarest = RarestFirstSelector.randomizedRarest();
        testSelector(rarest);
    }

    private void testSelector(RarestFirstSelector rarest) {
        rarest.initSelector(NUM_PIECES);
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(NUM_PIECES);

        statistics.setPiecesCount(0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0, rarest.getNextPieces(relevantPieces, statistics).toArray().length);

        statistics.setPiecesCount(0, 3, 0, 2, 1, 0, 0, 0);
        assertArrayEquals(new int[]{4, 3, 1}, rarest.getNextPieces(relevantPieces, statistics).toArray());

        statistics.setPieceCount(0, 1);
        final int[] nextPieces = rarest.getNextPieces(relevantPieces, statistics).toArray();
        if (nextPieces[0] == 0)
            assertArrayEquals(new int[]{0, 4, 3, 1}, nextPieces);
        else
            assertArrayEquals(new int[]{4, 0, 3, 1}, nextPieces);

    }

    /**
     * Test the randomized selector with a known random seed value to make sure the pieces are randomly organized
     */
    @Test
    public void testRandomizedSelectorRandomness() {
        final RarestFirstSelector rarest = new RandomizedRarestFirstSelector(new Random(0));
        rarest.initSelector(NUM_PIECES);
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(NUM_PIECES);
        statistics.setPiecesCount(IntStream.range(0, NUM_PIECES).map(i -> 2).toArray());
        assertArrayEquals(new int[]{0, 7, 3, 4, 6, 1, 2, 5},
                rarest.getNextPieces(relevantPieces, statistics).toArray());

        statistics.setPieceCount(2, 1);
        assertArrayEquals(new int[]{2, 0, 7, 3, 4, 6, 1, 5},
                rarest.getNextPieces(relevantPieces, statistics).toArray());
    }

    /**
     * Tests that if a piece is marked as not relevant, it is ignored.
     */
    @Test
    public void testNonRelevantPiecesIgnored() {
        final RarestFirstSelector rarest = new RandomizedRarestFirstSelector(new Random(1));
        rarest.initSelector(NUM_PIECES);
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(NUM_PIECES);

        relevantPieces.clear(1);
        statistics.setPiecesCount(IntStream.range(0, NUM_PIECES).map(i -> 2).toArray());
        assertArrayEquals(new int[]{2, 6, 7, 0, 3, 4, 5},
                rarest.getNextPieces(relevantPieces, statistics).toArray());
    }
}
