package bt.torrent.selector;

import bt.torrent.PieceStatistics;

import java.util.stream.Stream;

/**
 * Implements a continuous piece selection algorithm.
 *
 * @see BaseStreamSelector
 * @since 1.1
 */
public interface PieceSelector {

    /**
     * Select pieces based on the provided statistics.
     *
     * @return Stream of selected piece indices
     * @since 1.1
     */
    Stream<Integer> getNextPieces(PieceStatistics pieceStatistics);
}
