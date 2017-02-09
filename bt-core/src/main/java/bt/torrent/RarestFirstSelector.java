package bt.torrent;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.PriorityQueue;
import java.util.Random;

public class RarestFirstSelector extends StreamSelector {

    private static final Comparator<Long> comparator = new PackedIntComparator();
    private static final int RANDOMIZED_SELECTION_SIZE = 10;

    private PieceStatistics pieceStatistics;
    private Optional<Random> random;

    public RarestFirstSelector(PieceStatistics pieceStatistics, boolean randomized) {
        this.pieceStatistics = pieceStatistics;
        this.random = randomized ? Optional.of(new Random(System.currentTimeMillis())) : Optional.empty();
    }

    @Override
    protected PrimitiveIterator.OfInt createIterator() {
        LinkedList<Integer> queue = orderedQueue();
        return new PrimitiveIterator.OfInt() {
            @Override
            public int nextInt() {
                if (random.isPresent()) {
                    int i = Math.min(RANDOMIZED_SELECTION_SIZE, queue.size());
                    return queue.remove(random.get().nextInt(i));
                } else {
                    return queue.poll();
                }
            }

            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }
        };
    }

    // TODO: this is very inefficient when only a few pieces are needed,
    // and this for sure can be moved to PieceStatistics (that will be responsible for maintaining an up-to-date list)
    private LinkedList<Integer> orderedQueue() {
        PriorityQueue<Long> rarestFirst = new PriorityQueue<>(comparator);
        int piecesTotal = pieceStatistics.getPiecesTotal();
        for (int pieceIndex = 0; pieceIndex < piecesTotal; pieceIndex++) {
            int count = pieceStatistics.getCount(pieceIndex);
            if (count > 0) {
                long packed = (((long)pieceIndex) << 32) + count;
                rarestFirst.add(packed);
            }
        }
        LinkedList<Integer> result = new LinkedList<>();
        Long l;
        while ((l = rarestFirst.poll()) != null) {
            result.add((int)(l >> 32));
        }
        return result;
    }
}
