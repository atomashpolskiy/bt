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

import bt.torrent.selector.PackedIntComparator;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Implements the "rarest-first" piece selection algorithm.
 * As the name implies, pieces that appear less frequently
 * and are generally less available are selected in the first place.
 *
 * There are two "flavours" of the "rarest-first" strategy: regular and randomized.

 * Regular rarest-first selects whichever pieces that are the least available
 * (strictly following the order of increasing availability).
 *
 * Randomized rarest-first selects one of the least available pieces randomly
 * (which means that it does not always select THE least available piece, but rather looks at
 * some number N of the least available pieces and then randomly picks one of them).
 *
 * @since 1.0
 * @deprecated Since 1.1 replaced by {@link bt.torrent.selector.RarestFirstSelector}
 */
@Deprecated
public class RarestFirstSelectionStrategy implements PieceSelectionStrategy {

    private static final Comparator<Long> comparator = new PackedIntComparator();
    private static final RarestFirstSelectionStrategy selector = new RarestFirstSelectionStrategy(false);
    private static final RarestFirstSelectionStrategy randomizedSelector = new RarestFirstSelectionStrategy(true);

    /**
     * Regular rarest-first strategy.
     * Selects whichever pieces that are the least available
     * (strictly following the order of increasing availability).
     *
     * @since 1.0
     */
    public static RarestFirstSelectionStrategy rarest() {
        return selector;
    }

    /**
     * Randomized rarest-first strategy.
     * Selects one of the least available pieces randomly
     * (which means that it does not always select THE least available piece, but rather looks at
     * some number N of the least available pieces and then randomly picks one of them).
     *
     * @since 1.0
     */
    public static RarestFirstSelectionStrategy randomizedRarest() {
        return randomizedSelector;
    }

    private boolean randomized;

    private RarestFirstSelectionStrategy(boolean randomized) {
        this.randomized = randomized;
    }

    @Override
    public Integer[] getNextPieces(PieceStatistics pieceStats, int limit, Predicate<Integer> pieceIndexValidator) {

        PriorityQueue<Long> rarestPieces = new PriorityQueue<>(comparator);
        int piecesTotal = pieceStats.getPiecesTotal();
        for (int pieceIndex = 0; pieceIndex < piecesTotal; pieceIndex++) {
            int count = pieceStats.getCount(pieceIndex);
            if (count > 0 && pieceIndexValidator.test(pieceIndex)) {
                long packed = (((long)pieceIndex) << 32) + count;
                rarestPieces.add(packed);
            }
        }

        int collected = 0,
            k = randomized? limit * 3 : limit;
        Integer[] collectedIndices = new Integer[k];
        Long rarestPiece;
        while ((rarestPiece = rarestPieces.poll()) != null && collected < k) {
            collectedIndices[collected] = (int) (rarestPiece >> 32);
            collected++;
        }

        if (collected < k) {
            collectedIndices = Arrays.copyOfRange(collectedIndices, 0, collected);
        }

        if (collectedIndices.length > 0 && randomized) {
            Random random = new Random(System.currentTimeMillis());
            Set<Integer> selected = new HashSet<>((int)(collected / 0.75d + 1));
            Integer nextPiece;
            int actualLimit = Math.min(collected, limit);
            for (int i = 0; i < actualLimit; i++) {
                do {
                    nextPiece = collectedIndices[random.nextInt(collectedIndices.length)];
                } while (selected.contains(nextPiece));
                selected.add(nextPiece);
            }
            return selected.toArray(new Integer[actualLimit]);
        } else {
            return collectedIndices;
        }
    }
}
