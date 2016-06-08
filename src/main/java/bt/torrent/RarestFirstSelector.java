package bt.torrent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public class RarestFirstSelector implements PieceSelector {

    private static final Comparator<Long> comparator = new PackedIntComparator();

    private boolean randomized;

    public RarestFirstSelector(boolean randomized) {
        this.randomized = randomized;
    }

    @Override
    public Integer[] getNextPieces(long[] aggregateBitfield, int limit, Function<Integer, Boolean> pieceIndexValidator) {

        PriorityQueue<Long> rarestPieces = new PriorityQueue<>(comparator);

        for (int i = 0; i < aggregateBitfield.length; i++) {

            long bitmask = aggregateBitfield[i];
            for (int j = 0; j < 8; j++) {
                // unpack individual pieces count from bitmask
                int pieceIndex = i * 8 + j;
                if (!pieceIndexValidator.apply(pieceIndex)) {
                    continue;
                }
                // get (j+1)-th octet (from high to low) and normalize to an unsigned byte value
                int shift = ((7 - j) * 8);
                int count = (int) ((bitmask & (0xFFL << shift)) >> shift) & 0xFF; // it's an octet so cast is safe
                if (count > 0) {
                    // storing two ints in a long value: <piece-index><count>
                    long packed = (((long)pieceIndex) << 32) + count;
                    rarestPieces.add(packed);
                }
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
