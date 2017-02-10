package bt.torrent.selector;

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Decorator that applies a filter to the selector stream.
 *
 * @since 1.1
 */
public class ValidatingSelector implements PieceSelector {

    private Predicate<Integer> validator;
    private PieceSelector delegate;

    /**
     * Creates a filtering selector.
     *
     * @param validator Filter
     * @param delegate Delegate selector
     * @since 1.1
     */
    public ValidatingSelector(Predicate<Integer> validator, PieceSelector delegate) {
        this.validator = validator;
        this.delegate = delegate;
    }

    @Override
    public Stream<Integer> getNextPieces() {
        return delegate.getNextPieces().filter(validator::test);
    }
}
