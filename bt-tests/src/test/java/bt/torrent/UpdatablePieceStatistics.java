package bt.torrent;

public class UpdatablePieceStatistics implements PieceStatistics {

    private int[] piecesCount;

    UpdatablePieceStatistics(int piecesTotal) {
        this.piecesCount = new int[piecesTotal];
    }

    @Override
    public int getCount(int pieceIndex) {
        return piecesCount[pieceIndex];
    }

    public void setPiecesCount(int pieceIndex, int count) {
        if (pieceIndex < 0 || pieceIndex > piecesCount.length - 1) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex + "." +
                    " Expected 0.." + piecesCount.length);
        }
        piecesCount[pieceIndex] = count;
    }

    public void setPiecesCount(int... counts) {
        if (counts.length != piecesCount.length) {
            throw new IllegalArgumentException("Wrong number of pieces: " + counts.length + "." +
                    " Expected: " + piecesCount.length);
        }
        System.arraycopy(counts, 0, piecesCount, 0, piecesCount.length);
    }

    @Override
    public int getPiecesTotal() {
        return piecesCount.length;
    }
}
