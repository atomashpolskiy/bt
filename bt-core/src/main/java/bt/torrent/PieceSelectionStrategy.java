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

import java.util.function.Predicate;

/**
 * Implements a piece selection algorithm.
 *
 * @see bt.torrent.selector.PieceSelector
 * @since 1.0
 */
public interface PieceSelectionStrategy {

    /**
     * Returns an array of piece indices, selected from the overall piece statistics
     *
     * @param pieceStats Per-torrent piece statistics
     * @param limit Upper bound for the number of indices to collect
     * @param pieceIndexValidator Tells whether piece index might be selected.
     *                            Only pieces for which this function returns true have a chance to be selected.
     * @return Array of length lesser than or equal to {@code limit}
     * @since 1.0
     */
    Integer[] getNextPieces(PieceStatistics pieceStats, int limit, Predicate<Integer> pieceIndexValidator);
}
