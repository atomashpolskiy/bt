package bt.protocol;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PortMessageHandlerTest extends ProtocolTest {

    public PortMessageHandlerTest() {
        super(Collections.singletonMap(PortMessageHandler.PORT_ID, new PortMessageHandler()));
    }

    private byte[] PORT = new byte[]{0,0,0,3,9,/*--port*/24,0};
    private byte[] PORT_TRAILING_DATA = new byte[]{0,0,0,3,9,/*--port*/24,0,/*--trailing-data*/29,-3,0};

    @Test
    public void testProtocol_Port_ExactBytes() throws Exception {

        Port expected = new Port(24 * (2 << 7));
        assertDecodedAndHasAttributes(
                Port.class, PORT.length, expected, PORT);
    }

    @Test
    public void testProtocol_Port_TrailingBytes() throws Exception {

        Port expected = new Port(24 * (2 << 7));
        assertDecodedAndHasAttributes(
                Port.class, PORT.length, expected, PORT_TRAILING_DATA);
    }

    private byte[] PORT_INSUFFICIENT_DATA = new byte[]{0,0,0,3,9,/*--port*/24/*--pending-data...*/};

    @Test
    public void testProtocol_Port_InsufficientBytes() throws Exception {
        assertInsufficientDataAndNothingConsumed(
                Port.class, PORT_INSUFFICIENT_DATA);
    }

    private byte[] PORT_INVALID_DATA = new byte[]{0,0,0,127,9,/*--port*/24,0};

    @Test
    public void testProtocol_Port_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Port.class, 127, 2, PORT_INVALID_DATA);
    }

    @Override
    protected void assertMessageEquals(Message expected, Message actual) {

        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            throw new AssertionError("One of the messages is null and the other is not");
        }

        Class<? extends Message> expectedType = expected.getClass(), actualType = actual.getClass();
        if (!Port.class.equals(expectedType)) {
            throw new AssertionError("Unexpected message type: " + expectedType.getSimpleName().toLowerCase());
        }

        assertEquals(expectedType, actualType);
        Port expectedPort = (Port) expected, actualPort = (Port) actual;
        assertEquals(expectedPort.getPort(), actualPort.getPort());
    }
}
