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

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Decorator that applies a filter to the selector stream.
 *
 * @since 1.1
 */
public class ValidatingSelector implements PieceSelector {

    private Predicate<Integer> validator;
    private PieceSelector delegate;

    /**
     * Creates a filtering selector.
     *
     * @param validator Filter
     * @param delegate Delegate selector
     * @since 1.1
     */
    public ValidatingSelector(Predicate<Integer> validator, PieceSelector delegate) {
        this.validator = validator;
        this.delegate = delegate;
    }

    @Override
    public Stream<Integer> getNextPieces(PieceStatistics pieceStatistics) {
        return delegate.getNextPieces(pieceStatistics).filter(validator::test);
    }
}
