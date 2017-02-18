package bt.torrent;

import bt.test.torrent.selector.UpdatablePieceStatistics;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertArrayEquals;

public class SequentialSelectionStrategyTest {

    private static final Predicate<Integer> acceptAllValidator = i -> true;

    @Test
    public void testSelection() {
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(8);

        statistics.setPiecesCount(0, 0, 0, 0, 0, 0, 0, 0);
        assertArrayEquals(new Integer[0], SequentialSelectionStrategy.sequential().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPiecesCount(1, 0, 0, 0, 1, 0, 0, 0);
        assertArrayEquals(new Integer[] {0, 4}, SequentialSelectionStrategy.sequential().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPieceCount(1, 1);
        assertArrayEquals(new Integer[] {0, 1}, SequentialSelectionStrategy.sequential().getNextPieces(statistics, 2, acceptAllValidator));
    }
}
