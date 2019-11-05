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

package bt.event;

import bt.metainfo.TorrentId;
import bt.net.ConnectionKey;
import bt.net.Peer;

import java.util.Objects;

/**
 * Indicates, that a new connection with some peer has been established.
 *
 * @since 1.5
 */
public class PeerConnectedEvent extends BaseEvent implements TorrentEvent {

    private final ConnectionKey connectionKey;

    protected PeerConnectedEvent(long id, long timestamp, ConnectionKey connectionKey) {
        super(id, timestamp);
        this.connectionKey = Objects.requireNonNull(connectionKey);
    }

    @Override
    public TorrentId getTorrentId() {
        return connectionKey.getTorrentId();
    }

    /**
     * @since 1.5
     */
    public Peer getPeer() {
        return connectionKey.getPeer();
    }

    /**
     * @since 1.9
     */
    public int getRemotePort() {
        return connectionKey.getRemotePort();
    }

    /**
     * @since 1.9
     */
    public ConnectionKey getConnectionKey() {
        return connectionKey;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, connection key {" + connectionKey + "}";
    }
}
