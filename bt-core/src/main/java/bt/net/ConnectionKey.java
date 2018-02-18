/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

import bt.metainfo.TorrentId;

import java.util.Objects;

class ConnectionKey {
    private final Peer peer;
    private final TorrentId torrentId;

    public ConnectionKey(Peer peer, TorrentId torrentId) {
        Objects.requireNonNull(peer);
        Objects.requireNonNull(torrentId);
        this.peer = peer;
        this.torrentId = torrentId;
    }

    public Peer getPeer() {
        return peer;
    }

    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConnectionKey that = (ConnectionKey) obj;
        return peer.equals(that.peer) && torrentId.equals(that.torrentId);

    }

    @Override
    public int hashCode() {
        int result = peer.hashCode();
        result = 31 * result + torrentId.hashCode();
        return result;
    }
}
