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

/**
 * @since 1.0
 */
public final class Have implements Message {

    private int pieceIndex;

    /**
     * @since 1.0
     */
    public Have(int pieceIndex) throws InvalidMessageException {

        if (pieceIndex < 0) {
            throw new InvalidMessageException("Illegal argument: piece index (" + pieceIndex + ")");
        }

        this.pieceIndex = pieceIndex;
    }

    /**
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] piece index {" + pieceIndex + "}";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.HAVE_ID;
    }
}
