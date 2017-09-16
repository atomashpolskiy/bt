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

package bt.torrent.messaging;

import java.util.Arrays;
import java.util.Optional;

/**
 * Creates unique keys, that can be tested for equality with each other.
 *
 * @since 1.0
 */
public class Mapper {

    private static final Mapper instance = new Mapper();

    public static Mapper mapper() {
        return instance;
    }

    private Mapper() {}

    /**
     * Create a unique key for a block request, cancel request or received piece.
     *
     * @since 1.0
     */
    public Object buildKey(int pieceIndex, int offset, int length) {
        return new Key(pieceIndex, offset, length);
    }

    static Optional<Key> decodeKey(Object object) {
        return (object instanceof Key) ? Optional.of((Key) object) : Optional.empty();
    }

    static class Key {

        private final int[] key;

        Key(int pieceIndex, int offset, int length) {
            this.key = new int[] {pieceIndex, offset, length};
        }

        int[] getKey() {
            return key;
        }

        int getPieceIndex() {
            return key[0];
        }

        int getOffset() {
            return key[1];
        }

        int getLength() {
            return key[2];
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(key);
        }

        @Override
        public boolean equals(Object obj) {

            if (obj == null || !Key.class.equals(obj.getClass())) {
                return false;
            }
            return (obj == this) || Arrays.equals(key, ((Key) obj).getKey());
        }
    }
}
