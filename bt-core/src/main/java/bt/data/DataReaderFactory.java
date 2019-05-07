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

package bt.data;

import bt.event.EventSource;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *<p><b>Note that this class is not a part of the public API and is a subject to change.</b></p>
 */
public class DataReaderFactory {

    private Map<TorrentId, List<DataReaderChannel>> channelsByTorrentId;

    public DataReaderFactory(EventSource eventSource) {
        this.channelsByTorrentId = new HashMap<>();

        eventSource.onPieceVerified(e -> onPieceVerified(e.getTorrentId(), e.getPieceIndex()));
    }

    public DataReader createReader(Torrent torrent, DataDescriptor dataDescriptor) {
        return new DataReader() {
            @Override
            public ReadableByteChannel createChannel() {
                DataReaderChannel channel = new DataReaderChannel(dataDescriptor, torrent.getChunkSize());
                register(torrent.getTorrentId(), channel);
                return channel;
            }
        };
    }

    private synchronized void onPieceVerified(TorrentId torrentId, int pieceIndex) {
        List<DataReaderChannel> channels = channelsByTorrentId.get(torrentId);
        if (channels != null) {
            channels.forEach(channel -> channel.onPieceVerified(pieceIndex));
        }
    }

    private synchronized void register(TorrentId torrentId, DataReaderChannel channel) {
        channel.init();
        channelsByTorrentId.computeIfAbsent(torrentId, it -> new ArrayList<>())
                .add(channel);
    }
}
