package bt.torrent;

import java.util.HashMap;
import java.util.Map;

class Mapper {

    private static final Mapper instance = new Mapper();

    static Mapper mapper() {
        return instance;
    }

    private Mapper() {}

    Object buildKey(int pieceIndex, int offset, int length) {
        Map<String, Object> key = new HashMap<>();
        key.put("pieceIndex", pieceIndex);
        key.put("offset", offset);
        key.put("length", length);
        return key;
    }
}
