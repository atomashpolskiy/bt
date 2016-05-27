package bt.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class Protocol_InvalidDeclaredLengthTest {

    private byte[] CHOKE_INVALID_DATA = new byte[]{0,0,0,3,0};

    private byte[] UNCHOKE_INVALID_DATA = new byte[]{0,0,0,2,1};

    private byte[] INTERESTED_INVALID_DATA = new byte[]{0,0,0,4,2};

    private byte[] NOT_INTERESTED_INVALID_DATA = new byte[]{0,0,0,9,3};

    @Test
    public void testProtocol_Choke_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.CHOKE, 3, CHOKE_INVALID_DATA);
    }

    @Test
    public void testProtocol_Unchoke_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.UNCHOKE, 2, UNCHOKE_INVALID_DATA);
    }

    @Test
    public void testProtocol_Interested_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.INTERESTED, 4, INTERESTED_INVALID_DATA);
    }

    @Test
    public void testProtocol_NotInterested_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.NOT_INTERESTED, 9, NOT_INTERESTED_INVALID_DATA);
    }

    private byte[] HAVE_INVALID_DATA = new byte[]{0,0,0,1,4,/*--piece-index*/0,0,16,127};

    private byte[] REQUEST_INVALID_DATA = new byte[]{
            0,0,0,12,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};

    private byte[] CANCEL_INVALID_DATA = new byte[]{
            0,0,0,3,8,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};

    private byte[] PORT_INVALID_DATA = new byte[]{0,0,0,127,9,/*--port*/24,0};

    @Test
    public void testProtocol_Have_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.HAVE, 1, HAVE_INVALID_DATA);
    }

    @Test
    public void testProtocol_Request_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.REQUEST, 12, REQUEST_INVALID_DATA);
    }

    @Test
    public void testProtocol_Cancel_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.CANCEL, 3, CANCEL_INVALID_DATA);
    }

    @Test
    public void testProtocol_Port_InvalidLength() throws Exception {
        testProtocol_InvalidLength(MessageType.PORT, 127, PORT_INVALID_DATA);
    }

    private static void testProtocol_InvalidLength(MessageType type, int declaredLength, byte[] data) throws Exception {

        String expectedMessage = "Unexpected declared length for " + type.name() + ": " + declaredLength;
        InvalidMessageException e = null;
        try {
            Protocol.fromByteArray(new Message[1], data);
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }
}
