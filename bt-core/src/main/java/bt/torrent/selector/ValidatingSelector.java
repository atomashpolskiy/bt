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

import bt.data.LocalBitfield;
import bt.data.PeerBitfield;
import bt.torrent.PieceStatistics;

import java.util.BitSet;
import java.util.stream.IntStream;

/**
 * Decorator that applies a filter to the selector stream.
 *
 * @since 1.1
 */
public class ValidatingSelector {

    private final LocalBitfield localBitfield;
    private final BitSet piecesToSkip; // nullable
    private final PieceSelector delegate;

    /**
     * Creates a filtering selector.
     *
     * @param piecesToSkip The relevant pieces to download
     * @param delegate     Delegate selector
     * @since 1.1
     */
    public ValidatingSelector(LocalBitfield localBitfield, BitSet piecesToSkip, PieceSelector delegate) {
        this.localBitfield = localBitfield;
        this.piecesToSkip = piecesToSkip.isEmpty() ? null : piecesToSkip;
        this.delegate = delegate;
    }

    public IntStream getNextPieces(PeerBitfield peerBitfield, PieceStatistics pieceStatistics) {
        BitSet relevantChunks = peerBitfield.getBitmask();

        if (piecesToSkip != null)
            relevantChunks.andNot(this.piecesToSkip);

        localBitfield.removeVerifiedPiecesFromBitset(relevantChunks);
        return delegate.getNextPieces(relevantChunks, pieceStatistics);
    }
}
