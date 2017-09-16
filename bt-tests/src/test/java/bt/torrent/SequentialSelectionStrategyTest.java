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

import bt.test.torrent.selector.UpdatablePieceStatistics;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertArrayEquals;

public class SequentialSelectionStrategyTest {

    private static final Predicate<Integer> acceptAllValidator = i -> true;

    @Test
    @SuppressWarnings("deprecation")
    public void testSelection() {
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(8);

        statistics.setPiecesCount(0, 0, 0, 0, 0, 0, 0, 0);
        assertArrayEquals(new Integer[0], SequentialSelectionStrategy.sequential().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPiecesCount(1, 0, 0, 0, 1, 0, 0, 0);
        assertArrayEquals(new Integer[] {0, 4}, SequentialSelectionStrategy.sequential().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPieceCount(1, 1);
        assertArrayEquals(new Integer[] {0, 1}, SequentialSelectionStrategy.sequential().getNextPieces(statistics, 2, acceptAllValidator));
    }
}
