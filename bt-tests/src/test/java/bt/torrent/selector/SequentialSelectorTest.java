package bt.torrent.selector;

import bt.test.torrent.selector.UpdatablePieceStatistics;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SequentialSelectorTest {

    @Test
    public void testSelector() {
        UpdatablePieceStatistics statistics = new UpdatablePieceStatistics(8);

        statistics.setPiecesCount(0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0, collect(SequentialSelector.sequential().getNextPieces(statistics)).length);

        statistics.setPiecesCount(2, 0, 0, 0, 1, 0, 0, 0);
        assertArrayEquals(new Integer[] {0, 4}, collect(SequentialSelector.sequential().getNextPieces(statistics)));

        statistics.setPieceCount(1, 1);
        assertArrayEquals(new Integer[] {0, 1, 4}, collect(SequentialSelector.sequential().getNextPieces(statistics)));
    }

    private static <T> Object[] collect(Stream<T> stream) {
        List<T> list = stream.collect(Collectors.toList());
        return list.toArray(new Object[list.size()]);
    }
}
