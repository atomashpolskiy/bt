package bt.torrent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class RarestFirstSelectionStrategy implements PieceSelectionStrategy {

    private static final Comparator<Long> comparator = new PackedIntComparator();
    private static final RarestFirstSelectionStrategy selector = new RarestFirstSelectionStrategy(false);
    private static final RarestFirstSelectionStrategy randomizedSelector = new RarestFirstSelectionStrategy(true);

    public static RarestFirstSelectionStrategy regular() {
        return selector;
    }

    public static RarestFirstSelectionStrategy randomized() {
        return randomizedSelector;
    }

    private boolean randomized;

    private RarestFirstSelectionStrategy(boolean randomized) {
        this.randomized = randomized;
    }

    @Override
    public Integer[] getNextPieces(PieceStatistics pieceStats, int limit, Predicate<Integer> pieceIndexValidator) {

        PriorityQueue<Long> rarestPieces = new PriorityQueue<>(comparator);
        int piecesTotal = pieceStats.getPiecesTotal();
        for (int pieceIndex = 0; pieceIndex < piecesTotal; pieceIndex++) {
            int count = pieceStats.getCount(pieceIndex);
            if (count > 0 && pieceIndexValidator.test(pieceIndex)) {
                long packed = (((long)pieceIndex) << 32) + count;
                rarestPieces.add(packed);
            }
        }

        int collected = 0,
            k = randomized? limit * 3 : limit;
        Integer[] collectedIndices = new Integer[k];
        Long rarestPiece;
        while ((rarestPiece = rarestPieces.poll()) != null && collected < k) {
            collectedIndices[collected] = (int) (rarestPiece >> 32);
            collected++;
        }

        if (collected < k) {
            collectedIndices = Arrays.copyOfRange(collectedIndices, 0, collected);
        }

        if (collectedIndices.length > 0 && randomized) {
            Random random = new Random(System.currentTimeMillis());
            Set<Integer> selected = new HashSet<>((int)(collected / 0.75d + 1));
            Integer nextPiece;
            int actualLimit = Math.min(collected, limit);
            for (int i = 0; i < actualLimit; i++) {
                do {
                    nextPiece = collectedIndices[random.nextInt(collectedIndices.length)];
                } while (selected.contains(nextPiece));
                selected.add(nextPiece);
            }
            return selected.toArray(new Integer[actualLimit]);
        } else {
            return collectedIndices;
        }
    }

    /**
     * Compares only the first 32 bits of Long values
     */
    private static class PackedIntComparator implements Comparator<Long> {

        @Override
        public int compare(Long o1, Long o2) {
            if (o1.intValue() > o2.intValue()) {
                return 1;
            } else if (o1.intValue() < o2.intValue()) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
