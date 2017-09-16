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

package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Optional;

/**
 * Provides basic information about the context of a message (both inbound and outbound).
 *
 * @since 1.0
 */
public class MessageContext {

    private Optional<TorrentId> torrentId;
    private Peer peer;
    private ConnectionState connectionState;

    MessageContext(Optional<TorrentId> torrentId, Peer peer, ConnectionState connectionState) {
        this.torrentId = torrentId;
        this.peer = peer;
        this.connectionState = connectionState;
    }

    /**
     * @return Optional torrent ID or empty if not applicable
     *         (e.g. if a message was received outside of a torrent processing session)
     * @since 1.0
     */
    public Optional<TorrentId> getTorrentId() {
        return torrentId;
    }

    /**
     * @return Remote peer
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }
}
