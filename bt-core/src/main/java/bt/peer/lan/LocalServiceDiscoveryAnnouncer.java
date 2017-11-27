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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Collection;

class LocalServiceDiscoveryAnnouncer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalServiceDiscoveryAnnouncer.class);

    private static final Charset ascii = Charset.forName("ASCII");

    private final AnnounceGroup group;
    private final Cookie cookie;
    private final Collection<Integer> localPorts;

    private volatile DatagramChannel channel;
    private final Object channelLock;

    private volatile boolean shutdown;

    public LocalServiceDiscoveryAnnouncer(
            AnnounceGroup group,
            Cookie cookie,
            Collection<Integer> localPorts) {

        this.group = group;
        this.cookie = cookie;
        this.localPorts = localPorts;
        this.channelLock = new Object();
    }

    public AnnounceGroup getGroup() {
        return group;
    }

    public void announce(Collection<TorrentId> ids) throws IOException {
        if (shutdown) {
            return;
        }

        ByteBuffer[] bufs = new ByteBuffer[localPorts.size()];
        int i = 0;
        for (Integer port : localPorts) {
            bufs[i] = ByteBuffer.wrap(buildMessage(ids, port));
            i++;
        }
        try {
            DatagramChannel channel = getChannel();
            for (ByteBuffer buf : bufs) {
                if (shutdown) {
                    break;
                }
                channel.send(buf, group.getAddress());
            }
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException e1) {
                LOGGER.error("Failed to close channel", e);
            } finally {
                channel = null; // reset channel
            }
            throw e;
        }
    }

    private DatagramChannel getChannel() throws IOException {
        if (channel == null) {
            synchronized (channelLock) {
                if (channel == null && !shutdown) {
                    DatagramChannel _channel = SelectorProvider.provider().openDatagramChannel();
                    _channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, group.getTimeToLive());
                    channel = _channel;

                    if (shutdown) {
                        // in case shutdown was called just before assigning channel
                        closeChannel();
                    }
                }
            }
        }
        return channel;
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
        buf.append(group.getAddress().getAddress().toString().substring(1));
        buf.append(":");
        buf.append(group.getAddress().getPort());
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

    public void shutdown() {
        shutdown = true;
        closeChannel();
    }

    private void closeChannel() {
        DatagramChannel _channel = channel;
        if (_channel != null && _channel.isOpen()) {
            try {
                _channel.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close channel", e);
            }
        }
    }
}
