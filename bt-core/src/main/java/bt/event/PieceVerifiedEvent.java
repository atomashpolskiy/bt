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

import bt.metainfo.TorrentId;

/**
 * Indicates that the downloading and verification of one of torrent's pieces has been finished.
 *
 * @since 1.8
 */
public class PieceVerifiedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final int pieceIndex;

    protected PieceVerifiedEvent(long id, long timestamp, TorrentId torrentId, int pieceIndex) {
        super(id, timestamp);
        this.torrentId = torrentId;
        this.pieceIndex = pieceIndex;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, torrent {" + torrentId + "}, piece index {" + pieceIndex + "}";
    }
}
