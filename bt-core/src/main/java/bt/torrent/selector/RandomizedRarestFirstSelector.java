package bt.torrent.selector;

import bt.torrent.PieceStatistics;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class RandomizedRarestFirstSelector extends RarestFirstSelector {
    private final Random random;
    private int[] iterationOrder = null;

    public RandomizedRarestFirstSelector() {
        this(null);
    }

    /**
     * Package local constructor for unit tests
     * @param random the random source
     */
    RandomizedRarestFirstSelector(Random random) {
        this.random = random;
    }

    @Override
    public void initSelector(int numPieces) {
        super.initSelector(numPieces);
        if (iterationOrder == null) {
            iterationOrder = generateIterationOrder(numPieces);
        }
    }

    private int[] generateIterationOrder(int numPieces) {
        int[] order = new int[numPieces];
        Arrays.setAll(order, i -> i);
        ShuffleUtils.shuffle(order, random == null ? ThreadLocalRandom.current() : this.random);
        return order;
    }

    @Override
    protected int getIterationIdx(int i) {
        return iterationOrder[i];
    }

    @Override
    public IntStream getNextPieces(BitSet relevantChunks, PieceStatistics pieceStatistics) {
        if (iterationOrder == null || pieceStatistics.getPiecesTotal() != iterationOrder.length) {
            throw new IllegalStateException("Selector was not initialized");
        }

        return super.getNextPieces(relevantChunks, pieceStatistics);
    }
}
