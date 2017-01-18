package bt.test.protocol;

import bt.protocol.Piece;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

final class PieceMatcher implements BiPredicate<Piece, Piece> {

    @Override
    public boolean test(Piece piece, Piece piece2) {
        assertEquals(piece.getPieceIndex(), piece.getPieceIndex());
        assertEquals(piece.getOffset(), piece.getOffset());
        assertArrayEquals(piece.getBlock(), piece.getBlock());
        return true;
    }
}
