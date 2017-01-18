package bt.test.protocol;

import bt.protocol.Cancel;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;

final class CancelMatcher implements BiPredicate<Cancel, Cancel> {

    @Override
    public boolean test(Cancel cancel, Cancel cancel2) {
        assertEquals(cancel.getPieceIndex(), cancel2.getPieceIndex());
        assertEquals(cancel.getOffset(), cancel2.getOffset());
        assertEquals(cancel.getLength(), cancel2.getLength());
        return true;
    }
}
