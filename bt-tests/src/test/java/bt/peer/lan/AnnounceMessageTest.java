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

package bt.peer.lan;

import bt.metainfo.TorrentId;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static bt.peer.lan.AnnounceMessage.calculateMessageSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnnounceMessageTest {
    private static final TorrentId torrentId = TorrentId.fromBytes(new byte[]{1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6,6,6,7,8});

    /******************************************/
    /********* Message size estimates *********/
    /******************************************/
    // use values for maximum length
    private static final String MESSAGE_BODY_WITHOUT_INFOHASH =
            "BT-SEARCH * HTTP/1.1$$" +
                    "Host: [ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff]:65535$$" +
                    "Port: 65535$$" +
                    "cookie: 7fffffff$$" +
                    "$$" +
                    "$$";

    private static final String INFOHASH_HEADER =
            "Infohash: 1234567890123456789012345678901234567890$$";

    @Test
    public void testMessage_Size_SingleTorrent() {
        int actualLength = MESSAGE_BODY_WITHOUT_INFOHASH.length() + INFOHASH_HEADER.length();
        assertTrue("Actual length is bigger than estimate", actualLength <= calculateMessageSize(1));
    }

    @Test
    public void testMessage_Size_SeveralTorrents() {
        int actualLength = MESSAGE_BODY_WITHOUT_INFOHASH.length() + INFOHASH_HEADER.length() * 5;
        assertTrue("Actual length is bigger than estimate", actualLength <= calculateMessageSize(5));
    }

    /******************************************/
    /********** Builder exceptions ************/
    /******************************************/
    @Test(expected = IllegalStateException.class)
    public void testMessage_Builder_RequiredFields1_MissingAll() {
        AnnounceMessage.builder().build();
    }

    @Test(expected = IllegalStateException.class)
    public void testMessage_Builder_RequiredFields2_MissingIds() {
        AnnounceMessage.builder().port(1234).cookie(Cookie.newCookie()).build();
    }

    @Test(expected = IllegalStateException.class)
    public void testMessage_Builder_RequiredFields3_MissingCookie() {
        AnnounceMessage.builder().port(1234).torrentId(torrentId).build();
    }

    @Test(expected = IllegalStateException.class)
    public void testMessage_Builder_RequiredFields4_MissingPort() {
        AnnounceMessage.builder().cookie(Cookie.newCookie()).torrentId(torrentId).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessage_Builder_InvalidPort1_Zero() {
        AnnounceMessage.builder().port(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessage_Builder_InvalidPort2_Negative() {
        AnnounceMessage.builder().port(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessage_Builder_InvalidPort3_TooLarge() {
        AnnounceMessage.builder().port(65536);
    }

    @Test(expected = NullPointerException.class)
    public void testMessage_Builder_NullCookie() {
        AnnounceMessage.builder().cookie(null);
    }

    @Test(expected = NullPointerException.class)
    public void testMessage_Builder_NullId() {
        AnnounceMessage.builder().torrentId(null);
    }

    /******************************************/
    /************* Serialization **************/
    /******************************************/
    private static final String TEST_MESSAGE = "BT-SEARCH * HTTP/1.1\r\n" +
                "Host: 0.0.0.0:0\r\n" +
                "Port: 1234\r\n" +
                "Infohash: 0101010202020303030404040505050606060708\r\n" +
                "cookie: 7fffffff\r\n" +
                "\r\n" +
                "\r\n";
    @Test
    public void testMessage_Serialization() throws Exception {
        AnnounceMessage message = AnnounceMessage.builder().cookie(Cookie.fromString("7fffffff")).port(1234).torrentId(torrentId).build();
        ByteBuffer buffer = ByteBuffer.allocate(calculateMessageSize(1));
        message.writeTo(buffer, new InetSocketAddress(0));
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String s = new String(bytes, Charset.forName("ASCII"));
        assertEquals(TEST_MESSAGE, s);
    }

    /******************************************/
    /**************** Parsing *****************/
    /******************************************/
    @Test
    public void testMessage_Parsing() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(TEST_MESSAGE.getBytes(Charset.forName("ASCII")));
        AnnounceMessage message = AnnounceMessage.readFrom(buffer);
        assertEquals(1234, message.getPort());
        assertEquals(1, message.getTorrentIds().size());
        assertEquals(torrentId, message.getTorrentIds().iterator().next());
        assertTrue(Cookie.sameValue(Cookie.fromString("7fffffff"), message.getCookie()));
    }
}
