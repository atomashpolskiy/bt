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

/**
 * @since 1.0
 */
public interface IPeerWorkerFactory {

    /**
     * Create a peer worker for a given peer.
     *
     * @since 1.0
     */
    PeerWorker createPeerWorker(Peer peer);

    /**
     * Create a torrent-aware peer worker for a given peer.
     *
     * @since 1.0
     */
    PeerWorker createPeerWorker(TorrentId torrentId, Peer peer);
}
