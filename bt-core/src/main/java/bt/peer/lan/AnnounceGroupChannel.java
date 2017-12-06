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

import bt.net.InternetProtocolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Repairable datagram channel.
 * If the invocation of {@link #send(ByteBuffer)} or {@link #receive(ByteBuffer)} results in an exception,
 * then the caller can call {@link #closeQuietly()} and retry the original operation, which will result in the creation of a new channel.
 *
 * @since 1.6
 */
public class AnnounceGroupChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnounceGroupChannel.class);

    private final AnnounceGroup group;
    private final Collection<NetworkInterface> networkInterfaces;

    private final Selector selector;
    private DatagramChannel channel;

    private final AtomicBoolean shutdown;

    /**
     * @param group Target announce group
     * @param selector Selector to use for opening local channel
     * @param networkInterfaces Network interfaces, on which to listen to incoming messages
     * @since 1.6
     */
    public AnnounceGroupChannel(AnnounceGroup group,
                                Selector selector,
                                Collection<NetworkInterface> networkInterfaces) {
        this.group = group;
        this.selector = selector;
        this.networkInterfaces = networkInterfaces;
        this.shutdown = new AtomicBoolean(false);
    }

    /**
     * @since 1.6
     */
    public AnnounceGroup getGroup() {
        return group;
    }

    /**
     * @since 1.6
     */
    public synchronized void send(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() == 0) {
            return;
        }

        int written;
        do {
            written = getChannel().send(buffer, group.getAddress());
        } while (buffer.hasRemaining() && written > 0);
    }

    /**
     * @since 1.6
     */
    public synchronized SocketAddress receive(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() == 0) {
            return null;
        }

        return getChannel().receive(buffer);
    }

    private synchronized DatagramChannel getChannel() throws IOException {
        if (channel == null || !channel.isOpen()) {
            if (shutdown.get()) {
                throw new IllegalStateException("Channel has been shut down");
            }
            ProtocolFamily protocolFamily = InternetProtocolUtils.getProtocolFamily(group.getAddress().getAddress());
            DatagramChannel _channel = selector.provider().openDatagramChannel(protocolFamily);
            _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            // bind to any-local before setting TTL
            int port = group.getAddress().getPort();
            if (protocolFamily == StandardProtocolFamily.INET) {
                _channel.bind(new InetSocketAddress(Inet4Address.getByName("0.0.0.0"), port));
            } else {
                _channel.bind(new InetSocketAddress(Inet6Address.getByName("[::]"), port));
            }
            int timeToLive = group.getTimeToLive();
            if (timeToLive != 1) {
                _channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, timeToLive);
            }

            for (NetworkInterface iface : networkInterfaces) {
                _channel.join(group.getAddress().getAddress(), iface);
            }

            _channel.configureBlocking(false);
            channel = _channel;
        }
        return channel;
    }

    /**
     * Close currently opened channel if present and prevent creation of new channels.
     *
     * @since 1.6
     */
    public synchronized void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            closeQuietly();
        }
    }

    /**
     * @since 1.6
     */
    public synchronized void closeQuietly() {
        if (channel != null) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to close channel", e);
            } finally {
                channel = null;
            }
        }
    }
}
