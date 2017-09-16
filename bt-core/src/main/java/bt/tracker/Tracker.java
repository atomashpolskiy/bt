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

package bt.tracker;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

/**
 * Generic tracker interface.
 *
 * @since 1.0
 */
public interface Tracker {

    /**
     * Build a tracker request for a given torrent.
     *
     * @param torrent Torrent containing tracker info
     * @return Tracker request builder
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #request(TorrentId)}
     */
    default TrackerRequestBuilder request(Torrent torrent) {
        return request(torrent.getTorrentId());
    }

    /**
     * Build a tracker request for a given torrent.
     *
     * @param torrentId Torrent ID
     * @return Tracker request builder
     * @since 1.3
     */
    TrackerRequestBuilder request(TorrentId torrentId);
}
