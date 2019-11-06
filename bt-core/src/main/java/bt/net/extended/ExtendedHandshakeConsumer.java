/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package bt.net.extended;

import bt.bencoding.model.BEInteger;
import bt.net.IPeerConnectionPool;
import bt.net.InetPeer;
import bt.protocol.extended.ExtendedHandshake;
import bt.torrent.annotation.Consumes;
import bt.torrent.messaging.MessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedHandshakeConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedHandshakeConsumer.class);

    private final IPeerConnectionPool connectionPool;

    public ExtendedHandshakeConsumer(IPeerConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Consumes
    public void consume(ExtendedHandshake message, MessageContext messageContext) {
        BEInteger peerListeningPort = message.getPort();
        if (peerListeningPort != null) {
            InetPeer peer = (InetPeer) messageContext.getConnectionKey().getPeer();
            int listeningPort = peerListeningPort.getValue().intValueExact();
            peer.setPort(listeningPort);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Updating listening port for peer {}:{} to {}",
                        peer.getInetAddress(), messageContext.getConnectionKey().getRemotePort(), listeningPort);
            }
            connectionPool.checkDuplicateConnections(messageContext.getConnectionKey().getTorrentId(), peer);
        }
    }
}
