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

package bt.magnet;

import bt.net.InetPeerAddress;
import bt.protocol.Protocols;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static bt.TestUtil.assertExceptionWithMessage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MagnetUriParserTest {

    @Test
    public void testParser_v1_AllParamsPresent() {
        String uriTemplate = "magnet:?xt=urn:btih:%s&dn=%s&tr=%s&tr=%s&x.pe=%s&x.pe=%s";

        byte[] torrentId = new byte[20];
        String displayName = "xyz";
        String trackerUrl1 = "http://jupiter.gx/ann";
        String trackerUrl2 = "udp://jupiter.gx:1111";
        String peer1 = "1.1.1.1:10000";
        String peer2 = "2.2.2.2:10000";
        String s = String.format(uriTemplate, Protocols.toHex(torrentId), displayName, trackerUrl1, trackerUrl2, peer1, peer2);

        MagnetUri uri = MagnetUriParser.parser().parse(s);
        assertArrayEquals(torrentId, uri.getTorrentId().getBytes());
        assertEquals(displayName, uri.getDisplayName().get());

        Collection<String> trackerUrls = uri.getTrackerUrls();
        assertEquals(2, trackerUrls.size());
        assertTrue(trackerUrls.contains(trackerUrl1));
        assertTrue(trackerUrls.contains(trackerUrl2));

        Collection<InetPeerAddress> peerAddresses = uri.getPeerAddresses();
        assertEquals(2, peerAddresses.size());
        Iterator<InetPeerAddress> iter = peerAddresses.iterator();
        assertEquals(new InetPeerAddress("1.1.1.1", 10000), iter.next());
        assertEquals(new InetPeerAddress("2.2.2.2", 10000), iter.next());
    }

    @Test
    public void testParser_v1_OnlyInfohashPresent() {
        String uriTemplate = "magnet:?xt=urn:btih:%s";

        byte[] torrentId = new byte[20];
        String s = String.format(uriTemplate, Protocols.toHex(torrentId));

        MagnetUri uri = MagnetUriParser.parser().parse(s);
        assertArrayEquals(torrentId, uri.getTorrentId().getBytes());
    }

    @Test
    public void testParser_v1_BothInfohashAndMultihashPresent() {
        String uriTemplate = "magnet:?xt=urn:btih:%s&xt=urn:btmh:%s";

        byte[] torrentId = new byte[20];
        byte[] multihash = new byte[20];
        Arrays.fill(multihash, (byte) 1);
        String s = String.format(uriTemplate, Protocols.toHex(torrentId), Protocols.toHex(multihash));

        MagnetUri uri = MagnetUriParser.parser().parse(s);
        assertArrayEquals(torrentId, uri.getTorrentId().getBytes());
    }

    @Test
    public void testParser_v1_MultipleInfohashesPresent() {
        String uriTemplate = "magnet:?xt=urn:btih:%s&xt=urn:btih:%s";

        byte[] torrentId1 = new byte[20];
        byte[] torrentId2 = new byte[20];
        Arrays.fill(torrentId2, (byte) 1);
        String s = String.format(uriTemplate, Protocols.toHex(torrentId1), Protocols.toHex(torrentId2));

        assertExceptionWithMessage(it -> MagnetUriParser.parser().parse(s),
                "Parameter 'xt' has invalid number of values with prefix 'urn:btih:': 2");
    }

    @Test
    public void testParser_v1_MultipleIdenticalInfohashesPresent() {
        String uriTemplate = "magnet:?xt=urn:btih:%s&xt=urn:btih:%s";

        byte[] torrentId = new byte[20];
        String s = String.format(uriTemplate, Protocols.toHex(torrentId), Protocols.toHex(torrentId));

        MagnetUri uri = MagnetUriParser.parser().parse(s);
        assertArrayEquals(torrentId, uri.getTorrentId().getBytes());
    }

    @Test
    public void testParser_v1_InvalidPeerAddress_NoException() {
        String uriTemplate = "magnet:?xt=urn:btih:%s&x.pe=%s";

        byte[] torrentId = new byte[20];
        String peer = "xxxyyyzzz";
        String s = String.format(uriTemplate, Protocols.toHex(torrentId), peer);

        MagnetUri uri = MagnetUriParser.lenientParser().parse(s);
        assertArrayEquals(torrentId, uri.getTorrentId().getBytes());

        Collection<InetPeerAddress> peerAddresses = uri.getPeerAddresses();
        assertEquals(0, peerAddresses.size());
    }
}
