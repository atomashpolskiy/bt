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
import bt.net.buffer.BufferedData;

/**
 * @since 1.9
 */
public final class IncomingPiece implements Message {

    private int pieceIndex;
    private int offset;
    private BufferedData data;

    /**
     * @since 1.9
     */
    public IncomingPiece(int pieceIndex, int offset, BufferedData data) throws InvalidMessageException {
        if (pieceIndex < 0 || offset < 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + data.length() + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.data = data;
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
        return data.length();
    }

    /**
     * @since 1.9
     */
    public void writeDataTo(Range<?> range) {
        try {
            range.putBytes(data.buffer());
        } finally {
            data.dispose();
        }
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] piece index {" + pieceIndex + "}, offset {" + offset +
                "}, block {" + data.length() + " bytes}";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.PIECE_ID;
    }
}
