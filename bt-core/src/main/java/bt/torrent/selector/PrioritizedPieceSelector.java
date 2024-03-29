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

import bt.protocol.Protocols;
import bt.torrent.PieceStatistics;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A piece selector that allows chunks to be prioritized. Prioritized pieces can be changed after download is started.
 * Setting new piece priority is thread safe.
 */
public class PrioritizedPieceSelector implements PieceSelector {
    private final PieceSelector delegate;
    private final AtomicReference<BitSet> highPriorityPieces;

    public PrioritizedPieceSelector(PieceSelector delegate) {
        this.delegate = delegate;
        this.highPriorityPieces = new AtomicReference<>(new BitSet(0));
    }

    public void setHighPriorityPieces(BitSet highPriorityPieces) {
        this.highPriorityPieces.set(Protocols.copyOf(highPriorityPieces));
    }

    public void setHighPriorityPiecesIfNull(BitSet highPriorityPieces) {
        this.highPriorityPieces.compareAndSet(null, Protocols.copyOf(highPriorityPieces));
    }

    @Override
    public void initSelector(int numPieces) {
        delegate.initSelector(numPieces);
    }

    @Override
    public IntStream getNextPieces(BitSet relevantChunks, PieceStatistics pieceStatistics) {
        Supplier<IntStream> highPriorityStream = IntStream::empty;

        // call get() once to ensure we the pieces don't update in the middle
        final BitSet localHighPriorityPieces = highPriorityPieces.get();
        if (!localHighPriorityPieces.isEmpty()) {
            BitSet relevantHighPriorityPieces = Protocols.copyOf(localHighPriorityPieces);
            relevantHighPriorityPieces.and(relevantChunks);

            // don't delegate an extra time if there are no more high priority pieces left.
            if (!relevantHighPriorityPieces.isEmpty()) {
                highPriorityStream = () -> delegate.getNextPieces(relevantHighPriorityPieces, pieceStatistics);
                relevantChunks.andNot(localHighPriorityPieces);
            }
        }

        Supplier<IntStream> normalPriorityStream = () -> delegate.getNextPieces(relevantChunks, pieceStatistics);
        // use suppliers so normal priority isn't computed if it isn't necessary
        return Stream.of(highPriorityStream, normalPriorityStream)
                .flatMapToInt(Supplier::get);
    }
}
