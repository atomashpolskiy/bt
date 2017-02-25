package bt.protocol;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;

public class PortMatcher implements BiPredicate<Port, Port> {

    @Override
    public boolean test(Port port, Port port2) {
        assertEquals(port.getPort(), port2.getPort());
        return true;
    }
}
