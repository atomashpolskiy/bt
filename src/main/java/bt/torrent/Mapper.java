package bt.torrent;

import bt.protocol.Cancel;
import bt.protocol.Piece;
import bt.protocol.Request;

import java.util.HashMap;
import java.util.Map;

class Mapper {

    private static final Mapper instance = new Mapper();

    static Mapper mapper() {
        return instance;
    }

    private Mapper() {}

    Object keyForRequest(Request request) {
        return buildKey(request.getPieceIndex(), request.getOffset(), request.getLength());
    }

    Object keyForCancel(Cancel cancel) {
        return buildKey(cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength());
    }

    Object keyForPiece(Piece piece) {
        return buildKey(piece.getPieceIndex(), piece.getOffset(), piece.getBlock().length);
    }

    private Map<String, Object> buildKey(int pieceIndex, int offset, int length) {
        Map<String, Object> key = new HashMap<>();
        key.put("pieceIndex", pieceIndex);
        key.put("offset", offset);
        key.put("length", length);
        return key;
    }
}
