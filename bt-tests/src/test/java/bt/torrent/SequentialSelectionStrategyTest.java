package bt.torrent;

import org.junit.Test;

import java.util.function.Predicate;

import static bt.torrent.SequentialSelectionStrategy.sequential;
import static org.junit.Assert.assertArrayEquals;

public class SequentialSelectionStrategyTest {

    private static final Predicate<Integer> acceptAllValidator = i -> true;

    @Test
    public void testSelection() {
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(8);

        statistics.setPiecesCount(0, 0, 0, 0, 0, 0, 0, 0);
        assertArrayEquals(new Integer[0], sequential().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPiecesCount(1, 0, 0, 0, 1, 0, 0, 0);
        assertArrayEquals(new Integer[] {0, 4}, sequential().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPiecesCount(1, 1);
        assertArrayEquals(new Integer[] {0, 1}, sequential().getNextPieces(statistics, 2, acceptAllValidator));
    }
}
