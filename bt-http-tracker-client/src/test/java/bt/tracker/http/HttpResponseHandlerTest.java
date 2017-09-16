/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.tracker.http;

import bt.net.InetPeer;
import bt.net.Peer;
import bt.tracker.TrackerResponse;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HttpResponseHandlerTest {

    private HttpResponseHandler responseHandler;
    private Charset defaultCharset = Charset.forName("ISO-8859-1");

    @Before
    public void setUp() {
        responseHandler = new HttpResponseHandler();
    }

    @Test
    public void handleResponse_Success1() throws Exception {

        TrackerResponse trackerResponse = responseHandler.handleResponse(
                HttpResponseHandlerTest.class.getResourceAsStream("tracker_response_success1"), defaultCharset);

        assertNotNull(trackerResponse);
        assertTrue(trackerResponse.isSuccess());
        assertEquals(3591, trackerResponse.getInterval());
        assertEquals(3591, trackerResponse.getMinInterval());

        assertHasPeers(trackerResponse.getPeers().iterator(),
                createPeer(null, new byte[]{77,(byte)245,113,10}, 13933),
                createPeer(null, new byte[]{37,17,49,(byte)228}, 6881),
                createPeer(null, new byte[]{(byte)178, (byte)215,98,37}, 16881),
                createPeer(null, new byte[]{46,(byte)167,127,46}, 44264),
                createPeer(null, new byte[]{91,(byte)247,(byte)233,(byte)253}, 6881),
                createPeer(null, new byte[]{(byte)176,117,(byte)218,(byte)136}, 39638),
                createPeer(null, new byte[]{37, (byte)190,(byte)197,(byte)178}, 62354),
                createPeer(null, new byte[]{77,105,(byte)190,5}, 15418),
                createPeer(null, new byte[]{(byte)176,99,67,43}, 28391));
    }

    private void assertHasPeers(Iterator<Peer> actual, Peer... expected) {

        int expectedLength = expected.length, actualLength = 0;
        while (actual.hasNext()) {

            if (expectedLength <= actualLength) {
                throw new RuntimeException("There are more peers than expected (" + expectedLength + ")");
            }

            actualLength++;
            Peer actualPeer = actual.next(), expectedPeer = expected[actualLength - 1];
            assertEquals(expectedPeer.getPeerId(), actualPeer.getPeerId());
            assertEquals(expectedPeer.getInetAddress(), actualPeer.getInetAddress());
            assertEquals(expectedPeer.getPort(), actualPeer.getPort());
        }

        if (actualLength < expectedLength) {
            throw new RuntimeException("There are less peers (" + actualLength +
                    ") than expected (" + expectedLength + ")");
        }
    }

    private Peer createPeer(byte[] peerId, byte[] inetAddressBytes, int port) {

        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByAddress(inetAddressBytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unexpected error", e);
        }

        return new InetPeer(inetAddress, port);
    }

    @Test
    public void handleResponse_Failure1() {

        TrackerResponse trackerResponse = responseHandler.handleResponse(
                HttpResponseHandlerTest.class.getResourceAsStream("tracker_response_failure1"), defaultCharset);

        assertNotNull(trackerResponse);
        assertFalse(trackerResponse.isSuccess());
        assertFalse(trackerResponse.getError().isPresent());
        assertEquals("Invalid info_hash (0 - )", trackerResponse.getErrorMessage());
    }
}
