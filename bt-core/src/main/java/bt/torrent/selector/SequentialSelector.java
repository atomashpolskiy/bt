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
