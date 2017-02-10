package bt.torrent.selector;

import java.util.stream.Stream;

/**
 * Implements a continuous piece selection algorithm.
 *
 * @see BaseStreamSelector
 * @since 1.1
 */
public interface PieceSelector {

    /**
     * @return Stream of selected piece indices
     * @since 1.1
     */
    Stream<Integer> getNextPieces();
}
