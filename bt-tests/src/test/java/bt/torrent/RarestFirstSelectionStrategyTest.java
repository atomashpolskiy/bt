package bt.torrent;

import bt.test.torrent.selector.UpdatablePieceStatistics;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertArrayEquals;

public class RarestFirstSelectionStrategyTest {

    private static final Predicate<Integer> acceptAllValidator = i -> true;

    @Test
    @SuppressWarnings("deprecation")
    public void testSelection() {
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(8);

        statistics.setPiecesCount(0, 0, 0, 0, 0, 0, 0, 0);
        assertArrayEquals(new Integer[0], RarestFirstSelectionStrategy.rarest().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPiecesCount(0, 3, 0, 2, 1, 0, 0, 0);
        assertArrayEquals(new Integer[] {4, 3}, RarestFirstSelectionStrategy.rarest().getNextPieces(statistics, 2, acceptAllValidator));

        statistics.setPieceCount(0, 1);
        assertArrayEquals(new Integer[] {0, 4}, RarestFirstSelectionStrategy.rarest().getNextPieces(statistics, 2, acceptAllValidator));
    }
}
