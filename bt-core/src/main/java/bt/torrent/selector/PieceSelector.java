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

import bt.torrent.PieceStatistics;

import java.util.BitSet;
import java.util.stream.IntStream;

/**
 * An interface for piece selection
 *
 * @since 1.1
 */
public interface PieceSelector {
    /**
     * Init any structures to iterate through numPieces pieces in some iteration order
     *
     * @param numPieces the number of pieces total
     */
    default void initSelector(int numPieces) {
        //do nothing
    }

    /**
     * Select pieces based on the chunks which are relevant. Relevant means that the peer we are selecting pieces for
     * has this chunk and we do not have this chunk locally
     *
     * @param relevantChunks  the relevant chunks to chose (in the peer's completed list,
     * @param pieceStatistics the piece statistics
     * @return the stream of the next pieces to get
     */
    IntStream getNextPieces(BitSet relevantChunks, PieceStatistics pieceStatistics);
}
