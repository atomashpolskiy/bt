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

package bt.peer;

import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.tracker.AnnounceKey;

/**
 * Shared registry of all peers, known to the current runtime.
 *
 * @since 1.0
 */
public interface IPeerRegistry {

    /**
     * Returns local peer, that represents current runtime in the swarm.
     *
     * @return Local peer
     * @since 1.0
     */
    Peer getLocalPeer();

    /**
     * Add peer for a given torrent and notify all peer consumers.
     *
     * @since 1.3
     */
    void addPeer(TorrentId torrentId, Peer peer);

    /**
     * Register a new tracker peer source for a given torrent, based on the provided announce key.
     * Note that the new peer source will NOT be used, if the torrent is private (as in BEP-27).
     *
     * @since 1.3
     */
    void addPeerSource(TorrentId torrentId, AnnounceKey announceKey);
}
