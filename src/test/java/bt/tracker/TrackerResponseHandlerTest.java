package bt.tracker;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TrackerResponseHandlerTest {

    private TrackerResponseHandler responseHandler;
    private Charset defaultCharset = Charset.forName("ISO-8859-1");

    @Before
    public void setUp() {
        responseHandler = new TrackerResponseHandler();
    }

    @Test
    public void handleResponse_Success1() throws Exception {

        TrackerResponse trackerResponse = responseHandler.handleResponse(
                TrackerResponseHandlerTest.class.getResourceAsStream("tracker_response_success1"), defaultCharset);

        assertNotNull(trackerResponse);
        assertTrue(trackerResponse.isSuccess());
        assertEquals(3591, trackerResponse.getInterval());
        assertEquals(3591, trackerResponse.getMinInterval());

        // TODO: add check for peers when parsing is ready
    }

    @Test
    public void handleResponse_Failure1() {

        TrackerResponse trackerResponse = responseHandler.handleResponse(
                TrackerResponseHandlerTest.class.getResourceAsStream("tracker_response_failure1"), defaultCharset);

        assertNotNull(trackerResponse);
        assertFalse(trackerResponse.isSuccess());
        assertEquals("Invalid info_hash (0 - )", trackerResponse.getErrorMessage());
    }

}
