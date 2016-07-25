package bt.protocol;

import org.junit.Test;

public class Protocol_InvalidDeclaredLengthTest extends ProtocolTest {

    private byte[] CHOKE_INVALID_DATA = new byte[]{0,0,0,3, StandardBittorrentProtocol.CHOKE_ID,1,2};

    private byte[] UNCHOKE_INVALID_DATA = new byte[]{0,0,0,2,StandardBittorrentProtocol.UNCHOKE_ID,1};

    private byte[] INTERESTED_INVALID_DATA = new byte[]{0,0,0,4,StandardBittorrentProtocol.INTERESTED_ID,1,2,3};

    private byte[] NOT_INTERESTED_INVALID_DATA = new byte[]{0,0,0,9,StandardBittorrentProtocol.NOT_INTERESTED_ID,1,2,3,4,5,6,7,8};

    @Test
    public void testProtocol_Choke_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Choke.class, 3, 0, CHOKE_INVALID_DATA);
    }

    @Test
    public void testProtocol_Unchoke_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Unchoke.class, 2, 0, UNCHOKE_INVALID_DATA);
    }

    @Test
    public void testProtocol_Interested_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Interested.class, 4, 0, INTERESTED_INVALID_DATA);
    }

    @Test
    public void testProtocol_NotInterested_InvalidLength() throws Exception {
        testProtocol_InvalidLength(NotInterested.class, 9, 0, NOT_INTERESTED_INVALID_DATA);
    }

    private byte[] HAVE_INVALID_DATA = new byte[]{0,0,0,1,4,/*--piece-index*/0,0,16,127};

    private byte[] REQUEST_INVALID_DATA = new byte[]{
            0,0,0,12,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};

    private byte[] CANCEL_INVALID_DATA = new byte[]{
            0,0,0,3,8,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};

    @Test
    public void testProtocol_Have_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Have.class, 1, 4, HAVE_INVALID_DATA);
    }

    @Test
    public void testProtocol_Request_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Request.class, 12, 12, REQUEST_INVALID_DATA);
    }

    @Test
    public void testProtocol_Cancel_InvalidLength() throws Exception {
        testProtocol_InvalidLength(Cancel.class, 3, 12, CANCEL_INVALID_DATA);
    }

}
