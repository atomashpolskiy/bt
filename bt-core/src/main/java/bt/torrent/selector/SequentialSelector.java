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

import bt.torrent.PieceStatistics;

import java.util.BitSet;
import java.util.stream.IntStream;

/**
 * Selects pieces sequentially in the order of their availability.
 *
 * @since 1.1
 **/
public class SequentialSelector implements PieceSelector {

    /**
     * @since 1.1
     */
    public static SequentialSelector sequential() {
        return new SequentialSelector();
    }

    @Override
    public IntStream getNextPieces(BitSet relevantChunks, PieceStatistics pieceStatistics) {
        return relevantChunks.stream();
    }
}
