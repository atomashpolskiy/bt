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

package bt.torrent.selector;

import bt.test.torrent.selector.UpdatablePieceStatistics;
import org.junit.Before;
import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SequentialSelectorTest {
    private static final int NUM_CHUNKS = 8;

    @Before
    public void setUp() throws Exception {
    }

    /**
     * Runs some simple tests against the sequential selector
     */
    @Test
    public void simpleTestSelector() {
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(NUM_CHUNKS);

        BitSet peerPieces = new BitSet();
        assertEquals(0, SequentialSelector.sequential().getNextPieces(peerPieces, statistics).toArray().length);

        peerPieces.set(0);
        peerPieces.set(4);
        assertArrayEquals(new int[]{0, 4}, SequentialSelector.sequential().getNextPieces(peerPieces, statistics).toArray());

        peerPieces.set(1);
        assertArrayEquals(new int[]{0, 1, 4}, SequentialSelector.sequential().getNextPieces(peerPieces, statistics).toArray());
    }
}
