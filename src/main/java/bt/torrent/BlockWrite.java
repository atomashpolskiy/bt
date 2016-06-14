package bt.torrent;

import bt.net.Peer;

class BlockWrite {

    private Peer peer;
    private int pieceIndex;
    private int offset;
    private byte[] block;

    private volatile boolean complete;
    private volatile boolean success;

    BlockWrite(Peer peer, int pieceIndex, int offset, byte[] block) {
        this.peer = peer;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = block;
    }

    void setComplete() {
        complete = true;
    }

    void setSuccess(boolean success) {
        this.success = success;
    }

    Peer getPeer() {
        return peer;
    }

    boolean isComplete() {
        return complete;
    }

    boolean isSuccess() {
        return success;
    }

    int getPieceIndex() {
        return pieceIndex;
    }

    int getOffset() {
        return offset;
    }

    byte[] getBlock() {
        return block;
    }
}
