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

import java.util.stream.Stream;

public class ExaminePieceSelector {
    public static final int PIECE_COUNT = 100;
    //public static final int BULK_SIZE = RarestFirstSelector.RandomizedIteratorOfInt.SELECTION_MIN_SIZE / 2;
    public static final int BULK_SIZE = 20;
    public static final char[] CHARS = new char[]{' ', '.', 'o', '#'};

    public static void main(String[] args) {
        final PieceSelector selector = RarestFirstSelector.randomizedRarest();
        //final PieceSelector selector = RarestFirstSelector.rarest();
        final Stream<Integer> pieces = selector.getNextPieces(getPieceStatistics(PIECE_COUNT));
        final int[] states = new int[PIECE_COUNT];
        pieces.forEach(index -> {
            incrementStarted(states);
            assert states[index] == 0;
            states[index]++;
            dump(states, BULK_SIZE);
        });
        for (int state : states) {
            if (state == 0) {
                return;
            }
        }
        do {
            incrementStarted(states);
        } while (dump(states, BULK_SIZE) < CHARS.length - 1);
    }

    private static void incrementStarted(int[] states) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] != 0) {
                states[i]++;
            }
        }
    }

    private static int dump(int[] states, int bulkSize) {
        int minState = Integer.MAX_VALUE;
        final StringBuilder sb = new StringBuilder();
        sb.append('|');
        for (int i = 0; i < states.length; i++) {
            final int state = states[i];
            final int index = state >= CHARS.length ? CHARS.length - 1 : state;
            sb.append(CHARS[index]);
            if (minState > state) {
                minState = state;
            }
            if ((i + 1) % bulkSize == 0) {
                sb.append('|');
            }
        }
        System.out.println(sb);
        return minState;
    }

    private static PieceStatistics getPieceStatistics(final int count) {
        return new PieceStatistics() {
            @Override
            public int getCount(int pieceIndex) {
                return pieceIndex/BULK_SIZE + 1;
            }

            @Override
            public int getPiecesTotal() {
                return count;
            }
        };
    }
}
