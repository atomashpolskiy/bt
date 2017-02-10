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
    public static SequentialSelector sequential(PieceStatistics pieceStatistics) {
        return new SequentialSelector(pieceStatistics);
    }

    private PieceStatistics pieceStatistics;

    private SequentialSelector(PieceStatistics pieceStatistics) {
        this.pieceStatistics = pieceStatistics;
    }

    @Override
    protected PrimitiveIterator.OfInt createIterator() {
        return new PrimitiveIterator.OfInt() {
            int i = 0;

            @Override
            public int nextInt() {
                while (pieceStatistics.getCount(i) == 0) {
                    i++;
                }
                return i;
            }

            @Override
            public boolean hasNext() {
                return i < pieceStatistics.getPiecesTotal();
            }
        };
    }
}
