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

import java.util.PrimitiveIterator;

/**
 * Selects pieces sequentially in the order of their availability.
 *
 * @since 1.1
 **/
public class SequentialSelector extends BaseStreamSelector {

    /**
     * @since 1.1
     */
    public static SequentialSelector sequential() {
        return new SequentialSelector();
    }

    @Override
    protected PrimitiveIterator.OfInt createIterator(PieceStatistics pieceStatistics) {
        return new PrimitiveIterator.OfInt() {
            int i = 0;

            @Override
            public int nextInt() {
                return i++;
            }

            @Override
            public boolean hasNext() {
                while (i < pieceStatistics.getPiecesTotal() && pieceStatistics.getCount(i) == 0) {
                    i++;
                }
                return i < pieceStatistics.getPiecesTotal();
            }
        };
    }
}
