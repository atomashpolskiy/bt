package bt.test.protocol;

import bt.protocol.Have;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;

final class HaveMatcher implements BiPredicate<Have, Have> {

    @Override
    public boolean test(Have have, Have have2) {
        assertEquals(have.getPieceIndex(), have2.getPieceIndex());
        return true;
    }
}
