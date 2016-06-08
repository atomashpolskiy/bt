package bt.torrent;

import java.util.function.Function;

public interface PieceSelector {

    /**
     * Returns an array of piece indices, selected from the aggregate bitfield
     *
     * @param aggregateBitfield Each element of this aggregate bitfield corresponds to
     *                          an element from the original bitfield with the same index
     *                          and therefore can store information about up to 8 torrent pieces.
     *                          But, in contrast to the standard bitfield that has 1 bit for each piece
     *                          indicating whether a peer has this piece, the "aggregate" bitfield
     *                          stores a number (an octet, or 8 bits -- an unsigned byte) for each piece that indicates
     *                          the total number of peers that have this piece.
     *                          As with the standard bitfield, last element can have a few spare trailing bits.
     *
     * @param limit Upper bound for the number of indices to collect
     * @param pieceIndexValidator Tells whether piece index might be selected.
     *                            Only pieces for which this function returns true have a chance to be selected.
     *
     * @return Array of length lesser than or equal to {@code limit}
     */
    Integer[] getNextPieces(long[] aggregateBitfield, int limit, Function<Integer, Boolean> pieceIndexValidator);
}
