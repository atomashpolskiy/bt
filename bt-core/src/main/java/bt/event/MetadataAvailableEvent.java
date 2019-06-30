/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package bt.event;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

/**
 * Indicates, that torrent's metadata has been fetched.
 *
 * @since 1.9
 */
public class MetadataAvailableEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Torrent torrent;

    protected MetadataAvailableEvent(long id, long timestamp, TorrentId torrentId, Torrent torrent) {
        super(id, timestamp);
        this.torrentId = torrentId;
        this.torrent = torrent;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.9
     */
    public Torrent getTorrent() {
        return torrent;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, torrent {" + torrentId + "}";
    }
}
