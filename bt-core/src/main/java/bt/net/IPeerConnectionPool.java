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

import bt.metainfo.TorrentId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Provides the main API for getting connections to remote peers.
 *
 * @since 1.0
 */
public interface IPeerConnectionPool {

    /**
     * @return Connection for a given peer, if exists; null otherwise
     * @since 1.0
     */
    PeerConnection getConnection(Peer peer);

    /**
     * Visit connections for a given torrent ID.
     *
     * @since 1.5
     */
    void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor);

    /**
     * Request to establish a connection with a remote peer for a given torrent ID.
     *
     * @return Future connection if it can be established
     * @since 1.0
     */
    CompletableFuture<Optional<PeerConnection>> requestConnection(TorrentId torrentId, Peer peer);
}
