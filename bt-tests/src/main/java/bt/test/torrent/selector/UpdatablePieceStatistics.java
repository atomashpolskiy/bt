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

package bt.test.torrent.selector;

import bt.torrent.PieceStatistics;

public class UpdatablePieceStatistics implements PieceStatistics {

    private int[] counts;

    public UpdatablePieceStatistics(int piecesTotal) {
        this.counts = new int[piecesTotal];
    }

    public void setPieceCount(int pieceIndex, int count) {
        checkPieceIndex(pieceIndex);
        counts[pieceIndex] = count;
    }

    public void setPiecesCount(int... newCounts) {
        if (counts.length != newCounts.length) {
            throw new IllegalArgumentException("Invalid number of pieces: " + newCounts.length +
                    "; expected: " + counts.length);
        }
        this.counts = newCounts;
    }

    @Override
    public int getCount(int pieceIndex) {
        checkPieceIndex(pieceIndex);
        return counts[pieceIndex];
    }

    private void checkPieceIndex(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= counts.length) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex +
                    "; expected 0.." + (counts.length - 1));
        }
    }

    @Override
    public int getPiecesTotal() {
        return counts.length;
    }
}
