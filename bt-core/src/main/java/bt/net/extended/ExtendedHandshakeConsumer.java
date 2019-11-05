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
import bt.net.InetPeer;
import bt.peer.IPeerRegistry;
import bt.protocol.extended.ExtendedHandshake;
import bt.torrent.annotation.Consumes;
import bt.torrent.messaging.MessageContext;
import com.google.inject.Inject;

public class ExtendedHandshakeConsumer {

    private final IPeerRegistry peerRegistry;

    @Inject
    public ExtendedHandshakeConsumer(IPeerRegistry peerRegistry) {
        this.peerRegistry = peerRegistry;
    }

    @Consumes
    public void consume(ExtendedHandshake message, MessageContext messageContext) {
        BEInteger peerListeningPort = message.getPort();
        if (peerListeningPort != null) {
            InetPeer peer = (InetPeer) messageContext.getConnectionKey().getPeer();
            peer.setPort(peerListeningPort.getValue().intValueExact());
            peerRegistry.addPeer(messageContext.getConnectionKey().getTorrentId(), peer);
        }
    }
}
