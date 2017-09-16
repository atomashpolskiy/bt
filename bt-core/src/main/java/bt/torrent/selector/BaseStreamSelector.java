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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Base class for stream-based selectors.
 *
 * @since 1.1
 */
public abstract class BaseStreamSelector implements PieceSelector {

    @Override
    public final Stream<Integer> getNextPieces(PieceStatistics pieceStatistics) {
        return StreamSupport.stream(() -> Spliterators.spliteratorUnknownSize(createIterator(pieceStatistics),
                characteristics()), characteristics(), false);
    }

    /**
     * Select pieces based on the provided statistics.
     *
     * @return Stream of piece indices in the form of Integer iterator
     * @since 1.1
     */
    protected abstract PrimitiveIterator.OfInt createIterator(PieceStatistics pieceStatistics);

    protected int characteristics() {
        return Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.ORDERED;
    }
}
