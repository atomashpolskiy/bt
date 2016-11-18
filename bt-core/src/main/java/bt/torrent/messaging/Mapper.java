package bt.torrent.messaging;

import java.util.Arrays;

/**
 * Creates unique keys, that can be tested for equality with each other.
 *
 * @since 1.0
 */
class Mapper {

    private static final Mapper instance = new Mapper();

    static Mapper mapper() {
        return instance;
    }

    private Mapper() {}

    /**
     * Create a unique key for a block request, cancel request or received piece.
     *
     * @since 1.0
     */
    Object buildKey(int pieceIndex, int offset, int length) {
        return new Key(pieceIndex, offset, length);
    }

    private static class Key {

        private final int[] key;

        Key(int pieceIndex, int offset, int length) {
            this.key = new int[] {pieceIndex, offset, length};
        }

        int[] getKey() {
            return key;
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
