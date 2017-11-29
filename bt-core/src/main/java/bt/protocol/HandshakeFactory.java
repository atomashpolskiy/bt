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

package bt.protocol;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.peer.IPeerRegistry;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class HandshakeFactory implements IHandshakeFactory {

    private static final int HANDSHAKE_RESERVED_LENGTH = 8;

    private Provider<IPeerRegistry> peerRegistry; // TODO: workaround for circular DI deps, maybe get rid of this completely?

    @Inject
    public HandshakeFactory(Provider<IPeerRegistry> peerRegistry) {
        this.peerRegistry = peerRegistry;
    }

    @Override
    public Handshake createHandshake(TorrentId torrentId) {
        try {
            return new Handshake(new byte[HANDSHAKE_RESERVED_LENGTH], torrentId,
                    peerRegistry.get().getLocalPeer().getPeerId().orElseThrow(() -> new BtException("Local peer is missing ID")));
        } catch (InvalidMessageException e) {
            throw new BtException("Failed to create handshake", e);
        }
    }
}
