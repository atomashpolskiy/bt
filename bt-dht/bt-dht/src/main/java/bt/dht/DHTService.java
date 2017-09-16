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

package bt.dht;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.stream.Stream;

/**
 * Provides an interface to DHT services
 *
 * @since 1.1
 */
public interface DHTService {

    /**
     * Creates a peer lookup for a given torrent.
     * Blocks the caller thread if the DHT services are unavailable at the moment of calling.
     *
     * @return Stream of peers.
     *         Retrieval of the next element might block.
     *         The stream ends when the current lookup is exhausted.
     * @since 1.1
     * @deprecated since 1.3 in favor of {@link #getPeers(TorrentId)}
     */
    Stream<Peer> getPeers(Torrent torrent);

    /**
     * Creates a peer lookup for a given torrent.
     * Blocks the caller thread if the DHT services are unavailable at the moment of calling.
     *
     * @return Stream of peers.
     *         Retrieval of the next element might block.
     *         The stream ends when the current lookup is exhausted.
     * @since 1.3
     */
    Stream<Peer> getPeers(TorrentId torrentId);

    /**
     * Add a DHT node.
     *
     * @param node DHT node
     * @since 1.1
     */
    void addNode(Peer node);
}
