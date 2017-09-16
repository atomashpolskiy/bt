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

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.PriorityQueue;
import java.util.Random;

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
 * @since 1.1
 **/
public class RarestFirstSelector extends BaseStreamSelector {

    private static final Comparator<Long> comparator = new PackedIntComparator();
    private static final int RANDOMIZED_SELECTION_SIZE = 10;

    /**
     * Regular rarest-first selector.
     * Selects whichever pieces that are the least available
     * (strictly following the order of increasing availability).
     *
     * @since 1.1
     */
    public static RarestFirstSelector rarest() {
        return new RarestFirstSelector(false);
    }

    /**
     * Randomized rarest-first selector.
     * Selects one of the least available pieces randomly
     * (which means that it does not always select THE least available piece, but rather looks at
     * some number N of the least available pieces and then randomly picks one of them).
     *
     * @since 1.1
     */
    public static RarestFirstSelector randomizedRarest() {
        return new RarestFirstSelector(true);
    }

    private Optional<Random> random;

    private RarestFirstSelector(boolean randomized) {
        this.random = randomized ? Optional.of(new Random(System.currentTimeMillis())) : Optional.empty();
    }

    @Override
    protected PrimitiveIterator.OfInt createIterator(PieceStatistics pieceStatistics) {
        LinkedList<Integer> queue = orderedQueue(pieceStatistics);
        return new PrimitiveIterator.OfInt() {
            @Override
            public int nextInt() {
                if (random.isPresent()) {
                    int i = Math.min(RANDOMIZED_SELECTION_SIZE, queue.size());
                    return queue.remove(random.get().nextInt(i));
                } else {
                    return queue.poll();
                }
            }

            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }
        };
    }

    // TODO: this is very inefficient when only a few pieces are needed,
    // and this for sure can be moved to PieceStatistics (that will be responsible for maintaining an up-to-date list)
    private LinkedList<Integer> orderedQueue(PieceStatistics pieceStatistics) {
        PriorityQueue<Long> rarestFirst = new PriorityQueue<>(comparator);
        int piecesTotal = pieceStatistics.getPiecesTotal();
        for (int pieceIndex = 0; pieceIndex < piecesTotal; pieceIndex++) {
            int count = pieceStatistics.getCount(pieceIndex);
            if (count > 0) {
                long packed = (((long)pieceIndex) << 32) + count;
                rarestFirst.add(packed);
            }
        }
        LinkedList<Integer> result = new LinkedList<>();
        Long l;
        while ((l = rarestFirst.poll()) != null) {
            result.add((int)(l >> 32));
        }
        return result;
    }
}
