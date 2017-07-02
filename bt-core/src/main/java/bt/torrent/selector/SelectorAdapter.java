package bt.torrent.selector;

import bt.torrent.PieceSelectionStrategy;
import bt.torrent.PieceStatistics;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Selector wrapper for selection strategies.
 *
 * @see PieceSelectionStrategy
 * @see PieceSelector
 * @since 1.1
 */
public class SelectorAdapter implements PieceSelector {

    private PieceSelectionStrategy strategy;
    private Predicate<Integer> validator;

    /**
     * Wraps the provided selection strategy as a selector.
     *
     * @param strategy Piece selection strategy
     * @since 1.3
     */
    public SelectorAdapter(PieceSelectionStrategy strategy) {
        this(strategy, i -> true);
    }

    /**
     * Wraps the provided selection strategy as a selector.
     *
     * @param strategy Piece selection strategy
     * @param validator Selection validator
     * @since 1.1
     */
    public SelectorAdapter(PieceSelectionStrategy strategy, Predicate<Integer> validator) {
        this.strategy = strategy;
        this.validator = validator;
    }

    @Override
    public Stream<Integer> getNextPieces(PieceStatistics pieceStatistics) {
        return Arrays.asList(strategy.getNextPieces(pieceStatistics, pieceStatistics.getPiecesTotal(), validator)).stream();
    }
}
