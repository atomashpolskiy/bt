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
