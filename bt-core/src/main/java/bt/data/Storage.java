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

package bt.data;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

/**
 * Data back-end. Provides storage for torrent files.
 *
 * @since 1.0
 */
public interface Storage {

    /**
     * Get a storage unit for a particular torrent file.
     *
     * @param torrent Torrent metainfo
     * @param torrentFile Torrent file metainfo
     * @return Storage unit for a single torrent file
     * @since 1.0
     */
    StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile);
}
