/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.processor;

import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.torrent.TorrentSessionState;

import java.util.List;
import java.util.Optional;

/**
 * Aggregates all data, that is specific to and required for some processing chain,
 * and is used to transfer the processing state between different stages of this chain.
 *
 * @since 1.3
 */
public interface ProcessingContext {

    /**
     * @return Torrent ID or {@link Optional#empty()}, if it's not known yet
     * @since 1.3
     */
    Optional<TorrentId> getTorrentId();

    /**
     * @return Torrent or {@link Optional#empty()}, if it's not known yet
     * @since 1.3
     */
    Optional<Torrent> getTorrent();

    /**
     * @return Processing state or {@link Optional#empty()}, if it's not initialized yet
     * @since 1.5
     */
    Optional<TorrentSessionState> getState();

    /**
     * @return Get the storage for this torrent
     * @since 1.10
     */
    Storage getStorage();

    /**
     * Returns a list of all of the files that will be downloaded in this torrent, or empty if not known
     *
     * @return list of all of the files that will be downloaded in this torrent
     * @since 1.10
     */
    Optional<List<TorrentFile>> getAllNonSkippedFiles();
}
