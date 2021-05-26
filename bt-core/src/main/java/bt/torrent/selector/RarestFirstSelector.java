package bt.torrent.selector;

import bt.torrent.PieceStatistics;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.BitSet;
import java.util.stream.IntStream;

public class RarestFirstSelector implements PieceSelector {
    /**
     * Regular rarest-first selector.
     * Selects whichever pieces that are the least available
     * (strictly following the order of increasing availability).
     *
     * @since 1.1
     */
    public static RarestFirstSelector rarest() {
        return new RarestFirstSelector();
    }

    /**
     * Randomized rarest-first selector.
     * Selects one of the least available pieces randomly
     * (which means that it does not always select THE least available piece, but rather looks at
     * some number N of the least available pieces and then randomly picks one of them).
     *
     * @since 1.1
     */
    public static RarestFirstSelector randomizedRarest() {
        return new RandomizedRarestFirstSelector();
    }

    public RarestFirstSelector() {
    }

    @Override
    public IntStream getNextPieces(BitSet relevantChunks, PieceStatistics pieceStatistics) {
        int[] orderedReturnPieces = getChunksOrderedByRarity(relevantChunks, pieceStatistics);
        return Arrays.stream(orderedReturnPieces);
    }

    int[] getChunksOrderedByRarity(BitSet relevantChunks, PieceStatistics pieceStatistics) {
        // get a snapshot of the peer piece counts to avoid concurrency issues with changing data
        int[] piecePeerCounts = getPiecePeerCounts(relevantChunks, pieceStatistics);
        int totalRelevantPieces = (int) Arrays.stream(piecePeerCounts).filter(i -> i > 0).count();

        // compute the max number of peers that have an individual piece
        int maxPeerPieceCount = Ints.max(piecePeerCounts);

        // gives the offset of the output array in the bucket
        int[] sortedOffsetByPieceCount = getPieceBucketOffset(piecePeerCounts, maxPeerPieceCount);

        int[] orderedReturnPieces = createOrderedReturnPieces(piecePeerCounts, totalRelevantPieces, sortedOffsetByPieceCount);
        return orderedReturnPieces;
    }

    private int[] createOrderedReturnPieces(int[] piecePeerCounts, int totalRelevantPieces, int[] sortedOffsetByPieceCount) {
        int[] orderedReturnPieces = new int[totalRelevantPieces];
        for (int i = 0; i < piecePeerCounts.length; i++) {
            final int idx = getIterationIdx(i);

            int pieceCount = piecePeerCounts[idx];
            if (pieceCount > 0) {
                final int bucketOffset = sortedOffsetByPieceCount[pieceCount - 1]++;
                orderedReturnPieces[bucketOffset] = idx;
            }
        }
        return orderedReturnPieces;
    }

    protected int getIterationIdx(int i) {
        return i; // iterate in order
    }

    /**
     * Returns start and end boundaries for pieces ordered by peer piece count in a flat array
     *
     * @param piecePeerCounts   The array that contains the count of
     * @param maxPeerPieceCount the max number of peers that have the same piece
     * @return the boundary array
     */
    private int[] getPieceBucketOffset(int[] piecePeerCounts, int maxPeerPieceCount) {
        int[] pieceBucketOffset = new int[maxPeerPieceCount + 1];
        for (int pieceCount : piecePeerCounts)
            pieceBucketOffset[pieceCount]++;

        pieceBucketOffset[0] = 0;
        for (int i = 1; i < pieceBucketOffset.length; i++) {
            pieceBucketOffset[i] += pieceBucketOffset[i - 1];
        }
        return pieceBucketOffset;
    }

    private int[] getPiecePeerCounts(BitSet relevantChunks, PieceStatistics pieceStatistics) {
        int[] piecePeerCounts = new int[pieceStatistics.getPiecesTotal()];
        relevantChunks.stream().forEach(i -> piecePeerCounts[i] = pieceStatistics.getCount(i));
        return piecePeerCounts;
    }
}
