package bt.torrent;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class ValidatingSelector implements PieceSelector {

    private Predicate<Integer> validator;
    private PieceSelector delegate;

    public ValidatingSelector(Predicate<Integer> validator, PieceSelector delegate) {
        this.validator = validator;
        this.delegate = delegate;
    }

    @Override
    public Stream<Integer> getNextPieces() {
        return delegate.getNextPieces().filter(validator::test);
    }
}
