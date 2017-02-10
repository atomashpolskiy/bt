package bt.torrent.selector;

import bt.torrent.PieceStatistics;

import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Base class for stream-based selectors.
 *
 * @since 1.1
 */
public abstract class BaseStreamSelector implements PieceSelector {

    @Override
    public final Stream<Integer> getNextPieces(PieceStatistics pieceStatistics) {
        return StreamSupport.stream(() -> Spliterators.spliteratorUnknownSize(createIterator(pieceStatistics),
                characteristics()), characteristics(), false);
    }

    /**
     * Select pieces based on the provided statistics.
     *
     * @return Stream of piece indices in the form of Integer iterator
     * @since 1.1
     */
    protected abstract PrimitiveIterator.OfInt createIterator(PieceStatistics pieceStatistics);

    protected int characteristics() {
        return Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.ORDERED;
    }
}
