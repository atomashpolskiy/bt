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

package bt.processor.magnet;

import bt.data.Storage;
import bt.magnet.MagnetUri;
import bt.metainfo.TorrentId;
import bt.processor.torrent.TorrentContext;
import bt.torrent.fileselector.TorrentFileSelector;
import bt.torrent.messaging.BitfieldCollectingConsumer;
import bt.torrent.selector.PieceSelector;

import java.util.Optional;

public class MagnetContext extends TorrentContext {

    private final MagnetUri magnetUri;
    private volatile BitfieldCollectingConsumer bitfieldConsumer;

    public MagnetContext(MagnetUri magnetUri,
                         PieceSelector pieceSelector,
                         TorrentFileSelector fileSelector,
                         Storage storage) {
        super(pieceSelector, fileSelector, storage, null);
        this.magnetUri = magnetUri;
    }

    public MagnetContext(MagnetUri magnetUri, PieceSelector pieceSelector, Storage storage) {
        super(pieceSelector, storage, null);
        this.magnetUri = magnetUri;
    }

    public MagnetUri getMagnetUri() {
        return magnetUri;
    }

    @Override
    public Optional<TorrentId> getTorrentId() {
        return Optional.of(magnetUri.getTorrentId());
    }

    @Override
    public void setTorrentId(TorrentId torrentId) {
        throw new UnsupportedOperationException();
    }

    public BitfieldCollectingConsumer getBitfieldConsumer() {
        return bitfieldConsumer;
    }

    public void setBitfieldConsumer(BitfieldCollectingConsumer bitfieldConsumer) {
        this.bitfieldConsumer = bitfieldConsumer;
    }
}
