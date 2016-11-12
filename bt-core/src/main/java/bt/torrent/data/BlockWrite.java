package bt.torrent.data;

import bt.net.Peer;

public class BlockWrite {

    private Peer peer;
    private int pieceIndex;
    private int offset;
    private byte[] block;

    private volatile boolean complete;
    private volatile boolean success;

    public BlockWrite(Peer peer, int pieceIndex, int offset, byte[] block) {
        this.peer = peer;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = block;
    }

    public void setComplete() {
        complete = true;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Peer getPeer() {
        return peer;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getOffset() {
        return offset;
    }

    public byte[] getBlock() {
        return block;
    }
}
