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

package bt.protocol;

import bt.torrent.data.BlockReader;
import com.google.common.base.MoreObjects;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * @since 1.0
 */
public final class Piece implements Message {

    private final int pieceIndex;
    private final int offset;
    private final int length;
    private final BlockReader reader;

    // TODO: using BlockReader here is sloppy... just temporary
    public Piece(int pieceIndex, int offset, int length, BlockReader reader) throws InvalidMessageException {
        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + length + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.reader = reader;
    }

    // TODO: Temporary (used only for incoming pieces)
    public Piece(int pieceIndex, int offset, int length) throws InvalidMessageException {
        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + length + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.reader = null;
    }

    /**
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @since 1.0
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @since 1.9
     */
    public int getLength() {
        return length;
    }

    public boolean writeBlockTo(ByteBuffer buffer) {
        Objects.requireNonNull(reader);
        return reader.readTo(buffer);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("pieceIndex", pieceIndex)
                .add("offset", offset)
                .add("length", length)
                .toString();
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.PIECE_ID;
    }
}
