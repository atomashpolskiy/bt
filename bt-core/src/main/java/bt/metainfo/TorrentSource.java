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

package bt.metainfo;

import java.util.Optional;

/**
 * @since 1.3
 */
public interface TorrentSource {

    /**
     * Returns metadata that contains all necessary information to fully re-create the torrent.
     * Usually this means the contents of a .torrent file in BEP-3 format.
     * It's not mandatory for normal Bt operation.
     *
     * @return Torrent metadata
     * @since 1.3
     * @see MetadataService
     */
    Optional<byte[]> getMetadata();

    /**
     * Returns the part of metadata that is shared with other peers per BEP-9.
     * Usually this means the info dictionary.
     *
     * Programmatically created torrents may choose to use their own metadata serialization format,
     * given that the corresponding Bt services (like MetadataService) are adjusted accordingly
     * both for local and remote runtime instances.
     *
     * @return BEP-9 metadata
     * @since 1.3
     * @see MetadataService
     */
    byte[] getExchangedMetadata();

    // TODO: provide BEObjectModels for both types of metadata?
    // but this will effectively restrict possible types of metadata to bencoded form only...
}
