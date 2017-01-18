package bt.test.protocol;

import bt.protocol.Bitfield;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertArrayEquals;

final class BitfieldMatcher implements BiPredicate<Bitfield, Bitfield> {

    @Override
    public boolean test(Bitfield bitfield, Bitfield bitfield2) {
        assertArrayEquals(bitfield.getBitfield(), bitfield2.getBitfield());
        return true;
    }
}
