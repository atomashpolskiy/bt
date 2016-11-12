package bt.torrent.messaging;

import java.util.Arrays;

class Mapper {

    private static final Mapper instance = new Mapper();

    static Mapper mapper() {
        return instance;
    }

    private Mapper() {}

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
