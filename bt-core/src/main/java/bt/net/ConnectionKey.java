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
import com.google.common.base.MoreObjects;

import java.util.Objects;

public class ConnectionKey {
    private final Peer peer;
    private final int remotePort;
    private final TorrentId torrentId;

    public ConnectionKey(Peer peer, int remotePort, TorrentId torrentId) {
        Objects.requireNonNull(peer);
        Objects.requireNonNull(torrentId);
        this.peer = peer;
        this.remotePort = remotePort;
        this.torrentId = torrentId;
    }

    public Peer getPeer() {
        return peer;
    }

    public int getRemotePort() {
        return remotePort;
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
        // must not use peer's port, because it can be updated
        return peer.getInetAddress().equals(that.peer.getInetAddress())
                && remotePort == that.remotePort
                && torrentId.equals(that.torrentId);

    }

    @Override
    public int hashCode() {
        int result = peer.getInetAddress().hashCode();
        result = 31 * result + remotePort;
        result = 31 * result + torrentId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("peer", peer)
                .add("remotePort", remotePort)
                .add("torrentId", torrentId)
                .toString();
    }
}
