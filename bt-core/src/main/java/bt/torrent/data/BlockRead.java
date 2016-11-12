package bt.torrent.data;

import bt.net.Peer;

public class BlockRead {

    private Peer peer;
    private int pieceIndex;
    private int offset;
    private int length;
    private byte[] block;

    public BlockRead(Peer peer, int pieceIndex, int offset, int length, byte[] block) {
        this.peer = peer;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.block = block;
    }

    public Peer getPeer() {
        return peer;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public byte[] getBlock() {
        return block;
    }
}
