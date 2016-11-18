package bt.torrent;

/**
 * Provides basic information about
 * the availability of different pieces in the swarm.
 *
 * @since 1.0
 */
public interface PieceStatistics {

    /**
     * @return Total number of peers that have a given piece.
     * @since 1.0
     */
    int getCount(int pieceIndex);

    /**
     * @return Total number of pieces in the torrent (i.e. max piece index + 1)
     * @since 1.0
     */
    int getPiecesTotal();
}
