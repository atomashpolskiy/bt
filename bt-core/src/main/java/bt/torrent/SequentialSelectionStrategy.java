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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Selects pieces sequentially in the order of their availability.
 *
 * @since 1.0
 * @deprecated Since 1.1 replaced by {@link bt.torrent.selector.SequentialSelector}
 */
@Deprecated
public class SequentialSelectionStrategy implements PieceSelectionStrategy {

    private static final SequentialSelectionStrategy instance = new SequentialSelectionStrategy();

    /**
     * @since 1.0
     */
    public static SequentialSelectionStrategy sequential() {
        return instance;
    }

    @Override
    public Integer[] getNextPieces(PieceStatistics pieceStats, int limit, Predicate<Integer> pieceIndexValidator) {

        List<Integer> selected = null;

        int piecesTotal = pieceStats.getPiecesTotal();
        int selectedCount = 0;
        for (int i = 0; i < piecesTotal && selectedCount < limit; i++) {
            if (pieceStats.getCount(i) > 0 && pieceIndexValidator.test(i)) {
                if (selected == null) {
                    selected = new ArrayList<>();
                }
                selected.add(i);
                selectedCount++;
            }
        }

        return (selected == null) ? new Integer[0] : selected.toArray(new Integer[selected.size()]);
    }
}
