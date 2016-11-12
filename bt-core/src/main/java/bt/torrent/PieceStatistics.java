package bt.torrent;

public interface PieceStatistics {

    int getCount(int pieceIndex);

    /**
     * @return Total number of pieces (i.e. max piece index + 1)
     */
    int getPiecesTotal();
}
