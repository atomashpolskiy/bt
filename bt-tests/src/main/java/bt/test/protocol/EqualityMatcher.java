package bt.test.protocol;

import bt.protocol.Message;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

final class EqualityMatcher<T extends Message> implements BiPredicate<T, T> {

    @Override
    public boolean test(T t, T t2) {
        assertNotNull(t);
        assertNotNull(t2);
        assertEquals(t, t2);
        return true;
    }
}
