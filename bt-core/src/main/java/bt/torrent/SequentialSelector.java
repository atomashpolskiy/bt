package bt.torrent;

import java.util.PrimitiveIterator;

class SequentialSelector extends StreamSelector {

    private PieceStatistics pieceStatistics;

    SequentialSelector(PieceStatistics pieceStatistics) {
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
