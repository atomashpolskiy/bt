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

package bt.magnet;

import bt.protocol.extended.ExtendedMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * BEP-9 extension message.
 *
 * @since 1.3
 */
public class UtMetadata extends ExtendedMessage {

    /**
     * @since 1.3
     */
    public enum Type {

        /**
         * @since 1.3
         */
        REQUEST(0),

        /**
         * @since 1.3
         */
        DATA(1),

        /**
         * @since 1.3
         */
        REJECT(2);

        private final int id;

        Type(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        static Type forId(int id) {
            for (Type type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown message id: " + id);
        }
    }

    private static final String id = "ut_metadata";
    private static final String messageTypeField = "msg_type";
    private static final String pieceIndexField = "piece";
    private static final String totalSizeField = "total_size";

    static String id() {
        return id;
    }

    static String messageTypeField() {
        return messageTypeField;
    }

    static String pieceIndexField() {
        return pieceIndexField;
    }

    static String totalSizeField() {
        return totalSizeField;
    }

    /**
     * Create metadata request for a given piece.
     *
     * @param pieceIndex Piece index, non-negative
     * @since 1.3
     */
    public static UtMetadata request(int pieceIndex) {
        return new UtMetadata(Type.REQUEST, pieceIndex);
    }

    /**
     * Create metadata response for a given piece.
     *
     * @param pieceIndex Piece index, non-negative
     * @param totalSize Total size of the torrent's metadata, in bytes
     * @param data Requested piece's data
     * @since 1.3
     */
    public static UtMetadata data(int pieceIndex, int totalSize, byte[] data) {
        return new UtMetadata(Type.DATA, pieceIndex, totalSize, Objects.requireNonNull(data));
    }

    /**
     * Create metadata rejection response for a given piece.
     *
     * @param pieceIndex Piece index, non-negative
     * @since 1.3
     */
    public static UtMetadata reject(int pieceIndex) {
        return new UtMetadata(Type.REJECT, pieceIndex);
    }

    private final Type type;
    private final int pieceIndex;
    private final Optional<Integer> totalSize;
    private final Optional<byte[]> data;

    UtMetadata(Type type, int pieceIndex) {
        this(type, pieceIndex, null, null);
    }

    UtMetadata(Type type, int pieceIndex, Integer totalSize, byte[] data) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
        if (totalSize != null && totalSize <= 0) {
            throw new IllegalArgumentException("Invalid total size: " + totalSize);
        }
        this.type = type;
        this.pieceIndex = pieceIndex;
        this.totalSize = Optional.ofNullable(totalSize);
        this.data = Optional.ofNullable(data);
    }

    /**
     * @return Type of this metadata message
     * @since 1.3
     */
    public Type getType() {
        return type;
    }

    /**
     * @return Piece index, non-negative
     * @since 1.3
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @return Piece's data, when {@link #getType()} is {@link Type#DATA},
     *         or {@link Optional#empty()} otherwise
     * @since 1.3
     */
    public Optional<byte[]> getData() {
        return data;
    }

    /**
     * @return Total size of the torrent's metadata, when {@link #getType()} is {@link Type#DATA},
     *         or {@link Optional#empty()} otherwise
     * @since 1.3
     */
    public Optional<Integer> getTotalSize() {
        return totalSize;
    }

    @Override
    public String toString() {
        String s = "[" + this.getClass().getSimpleName() + "] type {" + type.name() + "}, piece index {" + pieceIndex + "}";
        if (type == Type.DATA) {
            s += ", data {" + data.get().length + " bytes}, total size {" + totalSize.get() + "}";
        }
        return s;
    }
}
