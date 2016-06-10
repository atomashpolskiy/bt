package bt.torrent;

import bt.protocol.Piece;

class BlockWrite {

    private Piece piece;
    private volatile boolean complete;
    private volatile boolean success;

    BlockWrite(Piece piece) {
        this.piece = piece;
    }

    void setSuccess(boolean success) {
        this.complete = true;
        this.success = success;
    }

    boolean isSuccess() {
        return success;
    }

    boolean isComplete() {
        return complete;
    }

    Piece getPiece() {
        return piece;
    }
}
