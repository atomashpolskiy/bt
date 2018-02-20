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

package bt.net;

import bt.data.Bitfield;
import bt.protocol.BitOrder;
import bt.protocol.Handshake;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Optional;

/**
 * Sends local bitfield to a newly connected remote peer.
 *
 * @since 1.0
 */
public class BitfieldConnectionHandler implements HandshakeHandler {

    private TorrentRegistry torrentRegistry;

    @Inject
    public BitfieldConnectionHandler(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        Optional<TorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(connection.getTorrentId());
        if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()
                && descriptorOptional.get().getDataDescriptor() != null) {
            Bitfield bitfield = descriptorOptional.get().getDataDescriptor().getBitfield();

            if (bitfield.getPiecesComplete() > 0) {
                Peer peer = connection.getRemotePeer();
                bt.protocol.Bitfield bitfieldMessage = new bt.protocol.Bitfield(bitfield.toByteArray(BitOrder.LITTLE_ENDIAN));
                try {
                    connection.postMessage(bitfieldMessage);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to send bitfield to peer: " + peer, e);
                }
            }
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        // do nothing
    }
}
