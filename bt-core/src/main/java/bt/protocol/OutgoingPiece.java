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

package bt.protocol;

import bt.data.range.Range;

import java.nio.ByteBuffer;

/**
 * @since 1.9
 */
public final class OutgoingPiece implements Message {

    private int pieceIndex;
    private int offset;
    private Range<?> range;

    /**
     * @since 1.9
     */
    public OutgoingPiece(int pieceIndex, int offset, Range<?> range) throws InvalidMessageException {
        if (pieceIndex < 0 || offset < 0 || range.length() == 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + range.length() + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.range = range;
    }

    /**
     * @since 1.9
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @since 1.9
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @since 1.9
     */
    public long length() {
        return range.length();
    }

    /**
     * @since 1.9
     */
    public void readDataTo(ByteBuffer buffer) {
        range.getBytes(buffer);
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] piece index {" + pieceIndex + "}, offset {" + offset +
                "}, block {" + range.length() + " bytes}";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.PIECE_ID;
    }
}
