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
import bt.protocol.Protocols;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Set;

class LocalServiceDiscoveryAnnouncer {
    private static final Charset ascii = Charset.forName("ASCII");

    private final AnnounceGroupChannel channel;
    private final Cookie cookie;
    private final Set<Integer> localPorts;

    public LocalServiceDiscoveryAnnouncer(
            AnnounceGroupChannel channel,
            Cookie cookie,
            Set<Integer> localPorts) {

        this.channel = channel;
        this.cookie = cookie;
        this.localPorts = localPorts;
    }

    public AnnounceGroup getGroup() {
        return channel.getGroup();
    }

    public void announce(Collection<TorrentId> ids) throws IOException {
        ByteBuffer[] bufs = new ByteBuffer[localPorts.size()];
        int i = 0;
        for (Integer port : localPorts) {
            bufs[i] = ByteBuffer.wrap(buildMessage(ids, port));
            i++;
        }
        for (ByteBuffer buf : bufs) {
            try {
                channel.send(buf);
            } catch (IOException e) {
                channel.closeQuietly();
                throw e;
            }
        }
    }

    /*
        BT-SEARCH * HTTP/1.1\r\n
        Host: <host>\r\n
        Port: <port>\r\n
        Infohash: <ihash>\r\n
        ...
        cookie: <cookie (optional)>\r\n
        \r\n
        \r\n
     */
    private byte[] buildMessage(Collection<TorrentId> ids, int localPort) {
        StringBuilder buf = new StringBuilder();

        buf.append("BT-SEARCH * HTTP/1.1\r\n");

        buf.append("Host: ");
        buf.append(channel.getGroup().getAddress().getAddress().toString().substring(1));
        buf.append(":");
        buf.append(channel.getGroup().getAddress().getPort());
        buf.append("\r\n");

        buf.append("Port: ");
        buf.append(localPort);
        buf.append("\r\n");

        ids.forEach(id -> {
            buf.append("Infohash: ");
            buf.append(Protocols.toHex(id.getBytes()));
            buf.append("\r\n");
        });

        buf.append("cookie: ");
        cookie.appendTo(buf);
        buf.append("\r\n");

        buf.append("\r\n");
        buf.append("\r\n");

        return buf.toString().getBytes(ascii);
    }
}
