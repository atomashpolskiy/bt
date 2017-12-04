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

package bt.torrent;

import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry of all torrents known to the current runtime.
 *
 * @since 1.0
 */
public interface TorrentRegistry {

    /**
     * Get all torrents, that have been registered in the runtime.
     *
     * @return All registered torrents
     * @since 1.2
     */
    Collection<Torrent> getTorrents();

    /**
     * Get all torrents, that have been registered in the runtime.
     *
     * @return All registered torrents
     * @since 1.3
     */
    Collection<TorrentId> getTorrentIds();

    /**
     * Get a torrent with a given torrent ID, if exists.
     *
     * @return {@link Optional#empty()} if this torrent ID is not known to the current runtime.
     * @since 1.0
     */
    Optional<Torrent> getTorrent(TorrentId torrentId);

    /**
     * Get a torrent descriptor for a given torrent, if exists.
     *
     * @return {@link Optional#empty()} if torrent descriptor hasn't been created yet.
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #getDescriptor(TorrentId)}
     */
    Optional<TorrentDescriptor> getDescriptor(Torrent torrent);

    /**
     * Get a torrent descriptor for a given torrent, if exists.
     *
     * @return {@link Optional#empty()} if torrent descriptor hasn't been created yet.
     * @since 1.3
     */
    Optional<TorrentDescriptor> getDescriptor(TorrentId torrentId);

    /**
     * Get an existing torrent descriptor for a given torrent
     * or create a new one if it does not exist.
     *
     * @param storage Storage to use for storing this torrent's files.
     *                Will be used when creating a new torrent descriptor.
     * @return Torrent descriptor
     * @since 1.0
     * @deprecated since 1.3 in favor of more clearly named {@link #register(Torrent, Storage)}
     */
    TorrentDescriptor getOrCreateDescriptor(Torrent torrent, Storage storage);

    /**
     * Get an existing torrent descriptor for a given torrent
     * or create a new one if it does not exist.
     *
     * @param storage Storage to use for storing this torrent's files.
     *                Will be used when creating a new torrent descriptor.
     * @return Torrent descriptor
     * @since 1.3
     */
    TorrentDescriptor register(Torrent torrent, Storage storage);

    /**
     * Get an existing torrent descriptor for a given torrent ID
     * or create a new one if it does not exist.
     *
     * @return Torrent descriptor
     * @since 1.3
     */
    TorrentDescriptor register(TorrentId torrentId);

    boolean isSupportedAndActive(TorrentId torrentId);
}
