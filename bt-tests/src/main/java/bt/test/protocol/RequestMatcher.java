package bt.test.protocol;

import bt.protocol.Request;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;

final class RequestMatcher implements BiPredicate<Request, Request> {

    @Override
    public boolean test(Request request, Request request2) {
        assertEquals(request.getPieceIndex(), request2.getPieceIndex());
        assertEquals(request.getOffset(), request2.getOffset());
        assertEquals(request.getLength(), request2.getLength());
        return true;
    }
}
