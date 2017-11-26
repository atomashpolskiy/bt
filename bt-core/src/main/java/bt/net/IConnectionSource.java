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

import java.util.concurrent.CompletableFuture;

/**
 * Provides the main API for getting connections to remote peers.
 *
 * @since 1.6
 */
public interface IConnectionSource {

    /**
     * Get connection for a given peer and torrent ID.
     * If the connection does not exist yet, then an attempt to establish a new ougoing connection is made.
     * Blocks until the connection is established.
     *
     * @return Newly established or existing connection or, possibly, an error in the form of {@link ConnectionResult}
     * @since 1.6
     */
    ConnectionResult getConnection(Peer peer, TorrentId torrentId);

    /**
     * Get connection for a given peer and torrent ID asynchronously.
     *
     * @return Future, which, when done, will contain a newly established or existing connection
     *         or, possibly, an error in the form of {@link ConnectionResult}
     * @since 1.6
     */
    CompletableFuture<ConnectionResult> getConnectionAsync(Peer peer, TorrentId torrentId);
}
