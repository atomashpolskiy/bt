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
import bt.net.ConnectionKey;
import bt.net.Peer;

/**
 * Provides basic information about the context of a message (both inbound and outbound).
 *
 * @since 1.0
 */
public class MessageContext {

    private final ConnectionState connectionState;
    private final ConnectionKey connectionKey;

    MessageContext(ConnectionKey connectionKey, ConnectionState connectionState) {
        this.connectionKey = connectionKey;
        this.connectionState = connectionState;
    }

    /**
     * @return Optional torrent ID or empty if not applicable
     *         (e.g. if a message was received outside of a torrent processing session)
     * @since 1.0
     */
    public TorrentId getTorrentId() {
        return connectionKey.getTorrentId();
    }

    /**
     * @return Remote peer
     * @since 1.0
     */
    public Peer getPeer() {
        return connectionKey.getPeer();
    }

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * @since 1.9
     */
    public ConnectionKey getConnectionKey() {
        return connectionKey;
    }
}
