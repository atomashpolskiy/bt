package bt.torrent;

import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class StreamSelector implements PieceSelector {

    @Override
    public Stream<Integer> getNextPieces() {
        return StreamSupport.stream(() -> Spliterators.spliteratorUnknownSize(createIterator(),
                characteristics()), characteristics(), false);
    }

    protected abstract PrimitiveIterator.OfInt createIterator();

    private int characteristics() {
        return Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.ORDERED;
    }
}
